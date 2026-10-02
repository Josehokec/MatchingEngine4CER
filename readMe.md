# OpenCEP Java

Java 17 implementation of [OpenCEP](https://github.com/ilya-kolchinsky/OpenCEP), based on upstream commit `e320ad874dd82e41cf89cea4459a2ca62d71ca84`. The runtime has no external dependencies and does not invoke Python. The Python modules have been reorganized into Java packages; this is a Java API, rather than a file-for-file translation.

## Run the example

Install JDK 17 or newer, then run from this directory:

```sh
./scripts/run.sh
```

[TestMain.java](src/main/java/TestMain.java) defines `SEQ(GOOG a, GOOG b, GOOG c)`, requires increasing peak prices, and uses a three-minute window. It supplies four sample input records and prints exactly one match and the evaluation plan.

For an input file and optional output file:

```sh
./scripts/run.sh test-data/stocks-demo.csv
./scripts/run.sh test-data/stocks-demo.csv target/matches.txt
```

Metastock input uses `ticker,YYYYMMDDhhmm,open,high,low,close,volume[,probability]`. Input timestamps must be nondecreasing; equal timestamps are accepted.

To build once and run the executable JAR:

```sh
./scripts/build.sh
java -jar target/opencep-java.jar
```

Maven is also supported:

```sh
mvn package
java -jar target/opencep-java-1.0.0-SNAPSHOT.jar
```

Both builds compile the same sources. The shell test command below runs the regression suites; Maven packaging alone does not run these standalone test programs.

## Use the library

```java
import java.time.Duration;
import java.util.List;
import opencep.CEP;
import opencep.base.*;
import opencep.condition.*;
import opencep.plugin.stocks.MetastockDataFormatter;
import opencep.stream.OutputStream;

Pattern pattern = new Pattern(
    new SeqOperator(
        new PrimitiveEventStructure("GOOG", "a"),
        new PrimitiveEventStructure("GOOG", "b")),
    new SmallerThanCondition(
        new Variable("a", "Peak Price"),
        new Variable("b", "Peak Price")),
    Duration.ofMinutes(3));

CEP cep = new CEP(pattern);
OutputStream<PatternMatch> output = new OutputStream<>();
cep.run(List.of(
    "GOOG,202001010900,100,101,99,100,1000",
    "GOOG,202001010901,101,102,100,101,1200"
), output, new MetastockDataFormatter());

for (PatternMatch match : output.snapshot()) {
    System.out.print(match);
}
```

Python attribute lambdas become Java lambdas, for example `new Variable("a", payload -> payload.get("Peak Price"))`. Java condition classes accept variables and constants; `SimpleCondition` accepts a predicate over a list of resolved argument values. Use `KCIndexCondition` and `KCValueCondition` for closure list predicates.

`CEP.runEvents(...)` accepts already-parsed Java events. `InputStream<T>` is a blocking producer/consumer stream; close it when the producer finishes. `FileInputStream` reads lines lazily. `FileOutputStream` writes matches without retaining their history. The engine closes its output after a run; in-memory outputs can be read through `snapshot()`, iteration, or `CEP.getPatternMatch()`.

## Features and configuration

Implemented components include primitive and nested `SEQ`, `AND`, `OR`, `NOT`, and Kleene closure patterns; composite conditions; time windows and occurrence probabilities; confidence thresholds; consumption, contiguity, and freeze policies; sorted storage and range indexes; ten tree planner choices; cost estimation; three adaptive optimizer choices; replay and simultaneous tree replacement; subtree sharing; tabu search and simulated annealing; group-by-key, RIP, and hypercube routing; preprocessing rules; and stock, sensor, and legacy tweet JSON adapters.

Settings use Java parameter records in `opencep.evaluation`, `opencep.adaptive`, `opencep.plan`, `opencep.parallel`, `opencep.tree`, and `opencep.transformation`. Adaptivity is disabled by default, as upstream. Set an optimizer's `updateInterval` to enable it. Cost-based initial planning uses supplied `Pattern.setStatistics(...)` values; without predefined statistics, initial planning uses the trivial tree.

The default evaluator uses actual aggregate time bounds, unique aggregate identities, complete contiguity constraints, and delayed evidence for nested negation. To reproduce the historical Python evaluator's matching behavior, enable compatibility mode:

```java
import opencep.evaluation.EvaluationMechanismParameters;
import opencep.parallel.ParallelExecutionParameters;

var evaluation = new EvaluationMechanismParameters().withLegacySemantics();
CEP compatible = new CEP(
    List.of(pattern), evaluation, new ParallelExecutionParameters(), null);
```

Compatibility mode intentionally reproduces historical aggregate identity/timestamp behavior, contiguity indexing, and pending-negative flushing. Its behavior was checked against the pinned Python snapshot. See [the migration notes](docs/migration.md) for source mappings, validation details, and supported combinations.

## Verify

```sh
./scripts/test.sh
```

The tests need only JDK 17 and the included fixtures. They run semantic and algorithm regressions and compare compatibility-mode matches and probabilities with 126 saved Python results, using both raw input streams and Java event objects. Fixture inputs contain at most 180 records each; these comparisons do not claim that every upstream benchmark or every possible configuration was reproduced.
