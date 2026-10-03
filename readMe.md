# MatchingEngine4CER

当前项目整理了三个可独立运行的 Java 匹配引擎 baseline。各引擎沿用自己的查询语言、输入格式和匹配语义；比较实验时请固定窗口、selection、consumption 与枚举方式。

| Baseline | Java 入口 / API | 构建与示例 | 详细说明 |
| --- | --- | --- | --- |
| OpenCEP | `TestMain` / `opencep.CEP` | `./scripts/run.sh` | [迁移说明](docs/migration.md) |
| CER-SRT / Wayeb | `cersrt.TestMain` / `cersrt.SrtEngine` | `./scripts/run-cer.sh` | [CER-SRT](docs/cer-srt.md) |
| CORE-CER | `corecer.CoreMain` / `corecer.CoreSession` | `./scripts/run-core.sh` | [CORE-CER](docs/core-cer.md) |

三个引擎的 Java 实现分别集中在 `src/main/java/opencep/`、`src/main/java/cersrt/`、`src/main/java/corecer/`。CORE 的算法实现和运行入口统一使用 `corecer.*` 包名；`src/main/java/TestMain.java` 继续作为原 OpenCEP 默认启动入口，运行命令保持下文所示。

## CORE-CER 快速运行

需要 **JDK 17+ 和 Maven 3**。从项目根目录执行；首次构建由 Maven 下载 ANTLR 与 JSON 依赖。

```sh
./scripts/build-core.sh
java -jar target/core/core-cer.jar --help
./scripts/run-core.sh
./scripts/test-core.sh
```

默认示例使用 `test-data/core-cer/examples/sequence.core` 和 6 条输入，完整枚举的预期结果是 **4 个匹配**。过滤和 Kleene 示例的预期结果分别为 **1** 与 **6**。

已验证：27 个原版 CORE 对照案例、704 个语义/API 检查和 23 个输入/CLI 检查通过；三套论文查询共 36 条全部可编译，三种真实数据各 1,000 条的短流可运行。

```sh
./scripts/run-core.sh --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/filter.core --events test-data/core-cer/examples/events.csv --stream S
./scripts/run-core.sh --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/kleene.core --events test-data/core-cer/examples/events.csv --stream S --output target/core-matches.jsonl
```

CORE 单独使用 `mvn -Pcore package` 构建，产物是包含依赖的 `target/core/core-cer.jar`。原有 OpenCEP/CER-SRT 构建和默认入口继续由下文命令使用。CORE 的原始 `.data` 描述文件入口、三套论文查询模板、可选真实数据下载、Java API、计时口径、适配范围与许可证见 [docs/core-cer.md](docs/core-cer.md)。

构建一次后，可以直接运行 JAR。下面固定最多读取 100,000 个事件、处理超时 30 秒、每条查询每次触发最多枚举 1,000 个结果；把查询与事件路径替换为自己的数据即可用于 baseline：

```sh
java -jar target/core/core-cer.jar \
  --declarations test-data/core-cer/examples/schema.core \
  --query test-data/core-cer/examples/sequence.core \
  --events test-data/core-cer/examples/events.csv --stream S \
  --enumeration-limit 1000 --max-events 100000 --timeout 30 \
  --output target/core-matches.jsonl
```

使用原始 CORE 声明/流描述文件也可以运行：

```sh
java -jar target/core/core-cer.jar -of \
  -q test-data/core-cer/examples/query.data \
  -s test-data/core-cer/examples/streams.data \
  -m false -n 100000 -t 30 -i 1000 -e true
```

标准输出是一条 JSON 运行摘要，`--output` 保存每行一个匹配的 JSONL；它们与原论文 driver 的 CSV 输出协议不同。`compileSeconds`、`loadSeconds`、`processSeconds` 分别记录查询编译、输入加载和匹配处理；`processSeconds` 扣除了枚举回调，`enumerationSeconds` 单独包含匹配复制与输出写入。

`--enumeration-limit 0` 完整枚举，默认也是 0。有限枚举的计数不一定是完整结果数，应同时检查 `truncated` 与 `matchesComplete`。`--no-enumeration` 只计识别触发，`matches` 为 null，`triggers` 不能当作匹配数量；它不能同时使用 `--output`。`inputLimited` 表明只使用了事件前缀，即使此前缀全部枚举，也不代表整个数据集已处理。原 `-m true` 的 GC 内存实验未移植。

原参数只兼容名称与描述文件格式：新 `-i 0` 表示完整枚举，新正值 `-n N` 精确限制为 N 个事件，与原实验 callback/循环的边界不同；`-e true/false` 均接受，JSON 始终包含运行时间。细节见 [CORE-CER 运行协议](docs/core-cer.md#原始-data-入口)。

## OpenCEP Java

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

The shell build and default Maven build compile the same OpenCEP and CER-SRT sources. CORE-CER uses the separate `core` Maven profile and is checked with `./scripts/test-core.sh`. The shell test command below runs the OpenCEP/CER-SRT regression suites; Maven packaging alone does not run these standalone test programs.

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

## CER-SRT Java engine

The additional `cersrt` package ports the Wayeb symbolic-register recognition engine from [ElAlev/cer-srt](https://github.com/ElAlev/cer-srt). It builds with the same Java 17 commands and has no external runtime dependencies.

```sh
./scripts/run-cer.sh
./scripts/run-cer.sh test-data/cer-srt/examples/stock.sre test-data/cer-srt/examples/stocks.stream --window 500
./scripts/benchmark-cer.sh test-data/cer-srt/examples/stock.sre test-data/cer-srt/examples/stocks.stream --windows 500,1000 --iterations 2 --output target/cer-benchmark.csv
```

[cersrt/TestMain.java](src/main/java/cersrt/TestMain.java) contains executable register-comparison, Kleene, and nested-block examples. The existing OpenCEP `TestMain.java` remains the default JAR entry point. `./scripts/test.sh` now also runs 716 CER semantic checks and all 43 upstream query fixtures (18,890 source matches), plus independent checks of corrected expression semantics.

See [CER-SRT instructions](docs/cer-srt.md) for stock/home/taxi examples, the Java API, query syntax, optimizations, benchmark commands, licensing, and scope. The default parser corrects the upstream multioperand repetition quirk; `--legacy` reproduces the tested historical behavior. This package covers the nondeterministic register matcher and Java experiment runner, rather than Wayeb's forecasting modules or the bundled independent SASE/Flink/Esper engines.
