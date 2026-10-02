# CER-SRT Java matching engine

The `cersrt` package ports the recognition path of Wayeb from [ElAlev/cer-srt](https://github.com/ElAlev/cer-srt), pinned to commit `688cb0617131024aa2538d0ddc6b5d500c3aaaea`. The repository contains a Scala Wayeb source archive, already-Java SASE/FlinkCEP/Esper adapters, Python experiment scripts, query templates, executables, and compressed datasets. This addition provides a standalone Java 17 symbolic-register matcher and a consolidated Java experiment runner. It has no Scala, Python, or external runtime dependencies and builds alongside OpenCEP.

## Executable examples

From the repository root:

```sh
./scripts/run-cer.sh
```

`cersrt.TestMain` runs three self-checking examples: register comparisons (2 matches), Kleene repetition (3 matches), and nested repeated blocks (4 matches). The existing default-package `TestMain` still runs the OpenCEP example.

File examples, each with exactly one match except the nested example, which produces four:

```sh
./scripts/run-cer.sh test-data/cer-srt/examples/stock.sre test-data/cer-srt/examples/stocks.stream --domain stock --window 500
./scripts/run-cer.sh test-data/cer-srt/examples/home.sre test-data/cer-srt/examples/homes.stream --domain home
./scripts/run-cer.sh test-data/cer-srt/examples/taxi.sre test-data/cer-srt/examples/taxis.stream --domain taxi
./scripts/run-cer.sh test-data/cer-srt/examples/nested.sre test-data/cer-srt/examples/nested.stream
```

Build once and invoke the Java entry point directly:

```sh
./scripts/build.sh
java -cp target/opencep-java.jar cersrt.TestMain
```

With Maven, use `mvn package` and `java -cp target/opencep-java-1.0.0-SNAPSHOT.jar cersrt.TestMain`. Both entry points are included in the same JAR; its default `java -jar` entry point remains the OpenCEP demo.

File mode accepts `--domain stock|home|taxi|json`, `--window 500`, `--legacy`, `--consume`, and `--limit 1000`. `--window` resolves the upstream `{window:TIMESTAMP}` placeholder; a numeric window already written in the query is preserved. `--consume` clears pending runs after all queries have processed an event that produces matches. `--limit` stops after that many input events.

## Java API

```java
import cersrt.*;
import java.util.List;
import java.util.Map;

String pattern = "#(;(IsEventTypePredicate(SELL)[\"x\"],"
    + "^(IsEventTypePredicate(BUY),GTAttr(price,\"x\"))))"
    + "{window:10}{windowType:time}";

List<Query> queries = new SreParser().parse(pattern);
SrtEngine engine = new SrtEngine(queries);
engine.accept(new Event(1, "SELL", 1, Map.of("price", 20.0)), System.out::println);
engine.accept(new Event(2, "BUY", 2, Map.of("price", 25.0)), System.out::println);
// MATCH query=0 partition=$ events=[1, 2]
System.out.println(engine.statistics());
```

`Expression` also provides Java builders (`type`, `atom`, `seq`, `choice`, `star`, `any`, and `next`), so a parser is optional. A `Guard` is a pure Java predicate over the current event and the register map. A register write takes place after its guard succeeds. Repeating a write updates the register to the most recently selected event. Unset registers make built-in register comparison predicates false.

Add domain predicates without reflection:

```java
PredicateRegistry predicates = new PredicateRegistry().register(
    "LargeVolume", 0, arguments -> (event, registers) -> event.number("volume") > 4000);
List<Query> queries = new SreParser(predicates).parse("LargeVolume{window:10}");
```

`accept(event)` returns that event's completed matches. `accept(event, callback)` and `run(events, callback)` deliver results without retaining completed-match history. `EventSource` is a lazy, single-use UTF-8 file reader; close it with try-with-resources. An engine belongs to one stream session and is not thread-safe. Callbacks must not reenter or reset it. If a guard, callback, or resource limit fails, abort that session and construct a new engine before replaying input.

## Query language and matching behavior

| Syntax | Meaning |
| --- | --- |
| `;(R1,R2,...)` | Contiguous concatenation by default |
| `+(R1,R2,...)` | Alternative expressions |
| `*(R)` | Zero or more repetitions, including nested repetitions |
| `#(;(R1,R2,...))` | Skip arbitrary events between the immediate operands |
| `#(*(R))` | Skip arbitrary events between repetitions of `R` |
| `@(;(R1,R2,...))` | Skip only events that fail the next single-event operand |
| `^(P,Q,...)`, `|(P,Q,...)`, `-P` | Boolean AND, OR, NOT on event guards |
| `P["x"]` | Store the selected event in register `x` |
| `GTAttr(price,"x")` | Compare current price with the price stored in `x` |
| `&` | Separate multiple queries |
| `{partitionBy:name}` | Evaluate independent event subsequences per key |
| `{window:N}{windowType:time}` | Require last selected timestamp minus first selected timestamp to be less than `N` |
| `{window:N}{windowType:count}` | Require the span of global input positions to be less than `N` |

Selection wrappers affect their immediate sequence or repetition boundaries. Nested expressions retain their own selection policy. `#(;(A,*(;(B,C)),D))` allows gaps around the repeated block, while each `B;C` block remains contiguous. Wrap the inner repetition in `#(...)` to allow gaps between blocks.

Built-in predicates include `IsEventTypePredicate`, `TruePredicate`, `FalsePredicate`, `EQStr`, `EQAttrStr`, numeric `EQ/NEQ/LT/LTE/GT/GTE`, and their `Attr` register variants. All predicates used by the 43 published Wayeb templates are included. Extra application predicates can be registered through the Java API.

Timestamps must be nondecreasing; equal timestamps are supported. Time values retain the input's units (no implicit seconds-to-milliseconds conversion). Window zero means unbounded, and the default window type is `count`, matching the recognition runtime. Count windows use input positions across all partitions, rather than external event IDs. Multiple matches may use the same input event. Equivalent accepting paths with the same selected positions and register valuation emit one match. Empty matches are suppressed. A selected standalone repetition can continue after emitting a match.

The stock adapter reads `BUY/SELL(id=...,name=...,volume=...,price=...,timestamp=...)`; the home adapter reads `LOAD(id=...,plug_timestamp=...,value=...,property=...,household_id=...)`; the taxi adapter reads `TRIP(...)` and normalizes zone spaces and `/` as upstream. Fields are read by name rather than fixed column offset. Generic JSON lines have `id`, `type`, `timestamp`, and an `attributes` object. Blank lines and `//` comments are skipped. Parse failures identify the file and line.

## Compatibility and scope

The default parser treats `*(R1,R2,...)` as repetition of the entire concatenated block. In the archived Scala parser, this form is accepted but its transformation retains only `R1`. The published `kn*.sre` templates contain this form, so their results can differ between the corrected default and the historical executable. The upstream optimized runtime also discards an accepting configuration immediately; the Java default allows a loop to continue after acceptance.

For the tested historical recognition behavior:

```sh
./scripts/run-cer.sh test-data/cer-srt/queries/stockqueries/kn1.sre your-stocks.stream --window 1000 --legacy
```

In Java, combine `new SreParser(new PredicateRegistry(), true)` with `new SrtEngine(queries, SrtEngine.Options.legacy())`. The parser's legacy flag keeps only the first operand of multioperand repetition; the engine flag stops accepting configurations. Compatibility is validated for the saved corpus, rather than promised for every malformed pattern or historical implementation bug. Java partition isolation works for both modes; the upstream optimized Scala path does not consistently apply partitioning, so cross-key bugs are not reproduced.

This port covers nondeterministic symbolic-register **recognition**. Determinization/unrolling, learned forecasting models, database connectors, Kafka integrations, archived maritime/video domains, and the other projects' independent matching engines are outside this package. `{order:N}` is parsed as metadata; recognition does not use Markov order. Regular-language complement `!(R)` is unsupported by the source NSRA compiler and is rejected explicitly here. `@` is supported for single-event operands or choices of such operands; a composite next operand would require complement construction and is rejected explicitly. Boolean guard negation remains available.

## Optimizations and source mapping

| Source families | Java implementation |
| --- | --- |
| `SREParser`, `SREFormula`, selection transformations | `SreParser`, immutable `Expression`, `Query` |
| Predicate reflection and separate comparison classes | `PredicateRegistry`, Java `Guard` factories |
| `NSRAUtils`, `SRA`, `SRATransition`, epsilon tracking | `CompiledPattern` |
| `Valuation`, configurations, `MonoRunNSRA`, run pools, `MatchList` | `SrtEngine`, `Match` |
| Stock, homes, taxi parsers and file sources | `EventFormats`, `EventSource` |
| Repeated per-domain experiment loops | `BenchmarkMain` |

Compilation caches epsilon closures and groups equal outgoing transition labels. Runtime configurations merge equivalent states, register valuations, and selected prefixes. Immutable linked prefixes share match history between branches instead of cloning whole event lists on every transition. Expired runs and idle partition entries are removed; no unconditional per-event logging remains.

Kleene and skip-till-any patterns can produce exponentially many distinct event groups; that output cost remains inherent. The default limit is one million active runs across all queries/partitions and throws explicitly if exceeded. Set `Options.maxActiveRuns` to another positive limit or zero to disable it. Without a window, pending runs can grow indefinitely.

## Benchmark and validation

The Java runner processes one query file or all `.sre` files below a directory and writes one CSV row per window and iteration:

```sh
./scripts/benchmark-cer.sh test-data/cer-srt/examples/stock.sre test-data/cer-srt/examples/stocks.stream --windows 500,1000 --iterations 2 --output target/cer-benchmark.csv
```

It measures elapsed file processing time, throughput, match counts, peak pending-run counts, and used heap sampled at the end of each iteration. Compilation is excluded from elapsed time. Heap samples include the JVM's other live/uncollected allocations and are not peak memory measurements. This runner replaces the duplicated local experiment workflow; it does not reproduce the publication's multi-system performance comparisons or launch external Esper/Flink/SASE installations.

```sh
./scripts/test.sh
```

The CER semantic suite performs **716 checks**, including 660 seeded comparisons with a separate finite-word interpreter. The parity suite compares all **43 published templates** with **18,890 saved matches** captured from the upstream `jars/wayeb.jar`, and checks corrected expressions against that independent interpreter. The fixture inputs are small synthetic native-format witnesses, including positive cases for every template. These tests do not reproduce the full compressed research datasets or establish performance equivalence. OpenCEP's existing regression and parity suites also run.

`test-data/cer-srt/upstream-provenance.json` records the commit, reference JAR checksum, fixture checksum, reference settings, and case totals. Queries retain their upstream content. Upstream Wayeb attribution and licensing terms are preserved in [licenses/wayeb-LICENSE.md](licenses/wayeb-LICENSE.md); those terms apply to the Wayeb-derived port and query fixtures, separately from the repository's root license.
