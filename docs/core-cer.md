# CORE-CER baseline

本项目将 [CORE-paper](https://github.com/CORE-cer/CORE-paper) 中的 CORE 匹配算法、查询编译器与执行结构整理为可独立构建的 Java baseline。CORE Java 源码统一放在 `src/main/java/corecer/`，包名统一为 `corecer.*`，保留上游匹配算法。`corecer.CoreSession` 提供同步 Java API，`corecer.CoreMain` 提供本地文件运行入口。

代码按职责放在 `corecer.exceptions`、`corecer.execution`、`corecer.parser`、`corecer.runtime` 和 `corecer.util` 子包；本地 CLI、输入适配和 session 位于 `corecer` 包。上游原始包名是 `edu.puc.core`，来源映射仍记录原路径，当前调用方应导入对应的 `corecer.*` 类型。例如运行时事件类型为 `corecer.runtime.events.Event`。ANTLR grammar 仍位于 `src/main/antlr4/CORE.g4`，生成的 parser 属于 `corecer.parser`。

论文实验仓库 [CORE-paper-experiments](https://github.com/CORE-cer/CORE-paper-experiments) 的 `src/` 只有 ESPER、FLINK、SASE；CORE Java 来自前一个仓库的 `experiments` 分支。这里保留实验仓库的三套 CORE 查询模板与声明文件，提供小型可验证输入。当前集成不等于完成整篇论文的全部实验复现。

## 构建和快速运行

需要 **JDK 17 或更新版本、Maven 3**。以下命令从项目根目录执行；首次构建需要 Maven 下载 ANTLR 和 JSON 依赖。

```sh
./scripts/build-core.sh
java -jar target/core/core-cer.jar --help
./scripts/run-core.sh
./scripts/test-core.sh
```

`build-core.sh` 执行 `mvn -Pcore package`。包含运行依赖的 JAR 为 `target/core/core-cer.jar`，CORE 的构建输出单独放在 `target/core/`。默认 Maven 构建及 `scripts/build.sh` 继续构建 OpenCEP/CER-SRT；它们的默认入口不变。

`run-core.sh` 会先构建，再运行传入的参数。无参数时使用 `examples/sequence.core`、共用声明文件和六条 CSV 事件。完整枚举的预期结果是 **4 个匹配**，过滤示例为 **1 个**，Kleene 示例为 **6 个**。

```sh
./scripts/run-core.sh --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/sequence.core --events test-data/core-cer/examples/events.csv --stream S
./scripts/run-core.sh --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/filter.core --events test-data/core-cer/examples/events.csv --stream S
./scripts/run-core.sh --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/kleene.core --events test-data/core-cer/examples/events.csv --stream S --output target/core-matches.jsonl
```

详细的手算预期索引在 [expected-matches.json](../test-data/core-cer/examples/expected-matches.json)。顺序查询为 `A; B; C`；两条 A 可以分别选择两条 B，并以最后一条 C 结束。过滤查询只接受索引 `[1, 4, 5]`。`A; B+; C` 对每条 A 可选择第一条 B、第二条 B 或两条 B，因此有六个匹配。

## 查询与输入

声明文件先定义事件字段和流的事件类型：

```text
DECLARE EVENT A(id long, value int)
DECLARE EVENT B(id long, value int)
DECLARE EVENT C(id long, value int)
DECLARE STREAM S(A, B, C)
```

查询示例：

```text
SELECT ALL *
FROM S
WHERE (A as X; B as Y; C as Z)
FILTER X[value > 5] AND Y[value > 8] AND Z[value > 5]
WITHIN 100 EVENTS
CONSUME BY NONE
```

事件字段按声明顺序传给 Java API；文件入口按字段名解析。CSV 必须有表头，支持逗号或分号分隔。事件类型列为 `event`，时间列为 `timestamp` 或 `time`，其余列提供声明中的属性。字符串可用双引号包围，双引号自身用两个双引号转义；不支持跨行 CSV 记录。

```csv
event,timestamp,id,value
A,1000,0,2
B,2000,1,9
C,3000,2,10
```

CSV 时间为毫秒整数，或 `yyyy-MM-dd HH:mm:ss` 格式的 UTC 时间。单个输入流必须按时间非递减排列，允许时间相同。多个描述文件输入源按时间稳定合并；时间相同时先按描述文件中的源顺序，再按文件中的行顺序。事件索引在加载并合并后从 **0** 开始，CSV 表头不计入索引。匹配的 `eventIndices` 指向处理顺序，和业务属性 `id` 是两个概念；这里的小示例特意让二者相等。

原始事件文本格式也保留：

```text
A(id=0,value=2)
B(id=1,value=9)
C(id=2,value=10)
```

字段可以带引号。文本记录可用 `__ts`、`timestamp` 或 `time` 提供事件时间；缺省时使用该文件内从 0 开始的记录序号。这个缺省时间适合事件数量窗口；使用秒级时间窗口时应显式提供毫秒时间。论文查询的 `[stock_time]` 等自定义窗口直接使用声明属性，不依赖这个缺省时间。

## 原始 `.data` 入口

查询描述文件 `query.data` 列出声明文件和查询文件，流描述文件 `streams.data` 指定流、输入格式及事件文件。相对路径先查找**进程当前工作目录**，不存在时再相对于描述文件目录解析；本项目样例使用项目根目录相对路径，应从项目根目录执行。

```text
FILE:./test-data/core-cer/examples/schema.core
FILE:./test-data/core-cer/examples/sequence.core
```

```text
S:FILE:./test-data/core-cer/examples/events.stream
```

兼容论文驱动程序的参数名称：

```sh
java -jar target/core/core-cer.jar -of \
  -q test-data/core-cer/examples/query.data \
  -s test-data/core-cer/examples/streams.data \
  -m false -n 100000 -t 30 -i 1000 -e true
```

入口兼容这组原始参数名称与描述文件格式，运行与计数协议使用**本项目的 JSON**。原论文 Python driver 预期的 `NumberOfEvents,EnumTime,Matches` 或 `TotalTime,NumberOfEvents,EnumTime,Matches` CSV 不应直接套用。匹配输出使用独立的 JSONL 文件，通过 `--output` 指定。

常用现代参数：

| 参数 | 含义 / 默认值 |
| --- | --- |
| `--declarations FILE --query FILE --events FILE` | 直接文件模式，不能与 `-q/-s` 描述文件模式混用 |
| `--stream S` | 直接文件模式的流名，默认 `S` |
| `--format csv\|events` | 默认 `csv`；`events` 对应 `E(attribute=value,...)` 文本 |
| `--max-events N` | 输入事件上限，默认不限制；`--max-events 0` 读取零事件 |
| `--timeout SECONDS` | 执行与枚举的合作式超时，默认 0 表示关闭；不包含编译和加载 |
| `--enumeration-limit N` | 每条查询每次触发的枚举上限，默认 0 表示完整枚举 |
| `--no-enumeration` | 只识别并统计触发；不能与 `--output` 同用 |
| `--output FILE.jsonl` | 流式写入已枚举的匹配快照，不在内存保留结果历史 |

原 `-n 0` 仍表示不限输入量；原 `-i` 对应枚举上限，`-t` 对应执行超时。`-of/-o/-f` 兼容接受，入口始终离线运行，不按真实时间节奏播放。`-e true/false` 兼容接受，JSON 始终提供各项时间。`-m false` 兼容接受；`-m true` 明确拒绝，因为原论文的 GC 内存实验没有移植到这个预加载输入的入口。

还有两处明确的入口计数差异：本项目 `-i 0` 表示全枚举，而原实验 callback 的 limit 0 会得到零输出；本项目正值 `-n N` 最多精确处理 N 个事件，原 `experiments Main` 的 `events <= N` 循环可能处理 N+1 个事件。这些属于运行协议适配；比较原论文驱动结果时需要统一枚举与事件上限，不能只复制同名参数。

对原事件文本直接运行时，例如：

```sh
java -jar target/core/core-cer.jar --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/sequence.core --events test-data/core-cer/examples/events.stream --format events --stream S
```

## 论文查询模板与真实数据

三套原始模板保持字节内容不变，来源与 SHA256 记录在 [upstream-provenance.json](../test-data/core-cer/upstream-provenance.json)。

| 本项目目录 | 事件类型及声明字段 | 窗口属性 |
| --- | --- | --- |
| `test-data/core-cer/queries/stock/` | `BUY` / `SELL`：`id int, name string, volume int, price double, stock_time int` | `stock_time` |
| `test-data/core-cer/queries/taxi/` | `TRIP`：`id int, medallion string, hack_license string, pickup_datetime long, dropoff_datetime long, trip_time_in_secs int, trip_distance double, pickup_zone string, dropoff_zone string, payment_type string, fare_amount double, surcharge double, mta_tax double, tip_amount double, tolls_amount double, total_amount double` | `dropoff_datetime` |
| `test-data/core-cer/queries/smarthome/` | `LOAD`：`id int, plug_timestamp int, value double, plug_id int, household_id int` | `plug_timestamp` |

每套都有 `descriptions/core.txt` 和 `q1.txt` 到 `q12.txt`。q1–q5 的顺序长度为 3、6、9、12、24；q6–q10 添加一个数据中不存在的末事件；q11 使用长度 3 加不存在的末事件比较窗口；q12 对应 `SELECT LAST`。

模板中的 `TIMESTAMP` 是原 Python driver 替换的**数字占位符**，不是最终可执行查询。先生成带具体窗口的查询，例如：

```sh
mkdir -p target
sed 's/TIMESTAMP/500/g' test-data/core-cer/queries/stock/q1.txt > target/core-stock-q1.core
```

生成结果包含 `WITHIN 500 [stock_time]`。这里 500 的单位就是输入 `stock_time` 数值的单位；自定义属性窗口不做时间单位转换。出租车、智能家居分别使用各自原始属性单位。

真实数据可选下载到 `target/`，不需要复制到版本管理目录：

```sh
mkdir -p target/core-data
curl -fL https://raw.githubusercontent.com/CORE-cer/CORE-paper-experiments/69c4ca1afeceefbed0682d75b1bf031599f9f785/streams.zip -o target/core-streams.zip
unzip -o target/core-streams.zip -d target/core-data
```

压缩包约 47 MB，展开约 318 MB，顶层文件为 `stocks.stream`、`taxi.stream`、`smarthomes.stream`。用股票 q1 运行一个最多读取 100,000 条事件的测试：

```sh
printf 'FILE:./test-data/core-cer/queries/stock/descriptions/core.txt\nFILE:./target/core-stock-q1.core\n' > target/core-stock-query.data
printf 'S:FILE:./target/core-data/stocks.stream\n' > target/core-stock-streams.data
java -jar target/core/core-cer.jar -of -q target/core-stock-query.data -s target/core-stock-streams.data -m false -n 100000 -t 30 -i 1000 -e true
```

记录数据 commit、窗口、事件限制、selection、consumption、是否完整枚举及 JVM 设置，才能与其他 baseline 对照。小型样例与查询编译检查不能代替完整论文性能实验。

## 匹配语义与兼容边界

保留上游 `;` 顺序、`OR`、`+`、命名与过滤、投影、分区、消费策略及 ECS 输出结构。selection 执行支持 `ALL`、`NEXT`、`LAST`、`MAX`，缺省为 `ALL`。语法中虽有 `ANY`、`STRICT` selection 名称，当前上游实现没有对应完整查询执行支持。`CONSUME BY ANY` 是消费策略，仍然支持；它与 `SELECT ANY` 不同。缺省消费策略为 `ANY`，比较所有组合时应显式使用 `CONSUME BY NONE`。

窗口保留上游严格边界：末事件与起始事件之差必须 **小于窗口值**，恰好等于窗口时不接受。`WITHIN 100 EVENTS` 使用事件索引差；`WITHIN 3 SECONDS` 使用毫秒时间差；`WITHIN 500 [stock_time]` 使用指定数值属性差。

通常应指定正窗口。无 `WITHIN` 时上游 `SimpleExecutor` 只在流初始位置启动一次：在 `A, A, B, B, C` 上运行 `A; B; C` 只得到包含第一条 A 的两个匹配；加 `WITHIN 100 EVENTS` 才得到四个匹配。这一行为保留为上游兼容语义。

保留上游表达式实现的边界。例如简单字段比较 `A[value >= 2]` 可运行，而算术组合 `A[value + 1 >= 3]` 会触发上游 `ValueException`。查询语法能被解析不意味着每一种组合都已具有完整执行支持；新增实验应先用小型见证输入核对语义。

投影也保留已验证的上游行为：`SELECT B,C ... WHERE A; B; C` 输出 B/C 索引，而 `SELECT B ... WHERE A; B; C` 因不包含最终触发事件会得到零结果。`WHERE` 中可以创建并过滤 alias，但 `SELECT` 中使用新 alias 会被上游第一遍查询解析器视作未知事件名；建议使用 `SELECT *` 或已声明的事件类型投影。

## Java API

JAR 中包含 API 和所需运行依赖，可以直接放到调用项目的 classpath。事件值严格按声明顺序提供；以下代码完整枚举四个结果：

```java
import corecer.CoreSession;
import java.util.ArrayList;
import java.util.List;

public class CoreApiExample {
    public static void main(String[] args) throws Exception {
        String schema = """
            DECLARE EVENT A(id long, value int)
            DECLARE EVENT B(id long, value int)
            DECLARE EVENT C(id long, value int)
            DECLARE EVENT N(id long, value int)
            DECLARE STREAM S(A, B, C, N)
            """;
        String query = """
            SELECT ALL * FROM S
            WHERE (A; B; C)
            WITHIN 100 EVENTS
            CONSUME BY NONE
            """;
        List<CoreSession.Match> matches = new ArrayList<>();
        try (CoreSession session = new CoreSession(schema, query, matches::add)) {
            session.sendEvent("S", "A", 1000L, 0L, 2);
            session.sendEvent("S", "A", 2000L, 1L, 8);
            session.sendEvent("S", "N", 3000L, 2L, 0);
            session.sendEvent("S", "B", 4000L, 3L, 7);
            session.sendEvent("S", "B", 5000L, 4L, 9);
            session.sendEvent("S", "C", 6000L, 5L, 10);
            System.out.println(session.metrics());
        }
        matches.forEach(match -> System.out.println(match.eventIndices()));
    }
}
```

一次 JVM 只允许一个活跃 `CoreSession`，因为上游事件声明、流声明、label 与 predicate 工厂含全局状态。使用 `try-with-resources` 关闭当前 session 后，可以再创建 session；新 session 的事件索引重新从 0 开始。不同 session 的运行时 Event 对象不能复用。

回调在 `sendEvent` 线程同步执行，不得在回调内递归发送事件或关闭 session。`CoreSession.Match` 是已复制的匹配快照，`eventIndices()` 为不可变列表，可安全保存；它不保存可变 ECS 链的引用。`queryNumber()` 从 1 开始，`triggerIndex()` 与列表中的事件索引从 0 开始。

`new CoreSession.Options(true, 0)` 表示完整枚举。非零 `enumerationLimit` 是**每条查询、每次输出触发**的枚举上限，不是整次运行的总数；此时已枚举数量可能小于完整结果。`new CoreSession.Options(false, 0)` 只运行识别过程并记录触发，不枚举匹配。`triggers` 不能当作 `enumeratedMatches`，也不能推算完整结果数。

## 计时与计数

查询编译、输入加载与事件对象构造在处理阶段之前完成，单独计时。处理超时从执行开始设置，不包含前两阶段；检查超时的频率会影响停止位置。

标准输出只有一条 JSON 摘要；时间字段为秒：

| 字段 | 口径 |
| --- | --- |
| `compileSeconds` | 声明、查询和执行器的构建，不含声明/查询文件读取 |
| `loadSeconds` | 事件输入读取、校验、合并和对象构造 |
| `processSeconds` | 各次 `sendEvent` 耗时扣除枚举回调耗时 |
| `enumerationSeconds` | 结果迭代、复制快照、同步 listener 和输出 write |
| `runSeconds` | 加载后的执行循环，包含处理和枚举 |
| `eventsPerSecond` | 已处理事件数 / `processSeconds` |

最终输出文件 `flush` 不计入这些执行时间。JSONL 每行有 `query`、`triggerIndex`、`eventIndices`；例如 `{"query":1,"triggerIndex":5,"eventIndices":[1,4,5]}`。`events` 是实际已处理事件数，`loadedEvents` 是加载数量，`matches` 在枚举启用时等于 `enumeratedMatches`，不枚举时为 null。

`CoreSession.metrics()` 提供已处理事件数、已枚举匹配数、输出触发数、被截断的触发数、处理纳秒数、枚举纳秒数与超时状态。`processNanos` 从 `sendEvent` 总耗时中扣除枚举回调耗时；`enumerationNanos` 包含迭代匹配、复制快照和 listener 工作，使用 `--output` 时也包含该 listener 的输出处理。

完整枚举、有限枚举与不枚举是三种不同测量口径。有限枚举或超时截断应连同 `truncated`、`truncatedTriggers`、`timedOut`、`matchesComplete` 状态一起报告；不枚举时已枚举匹配数为 0，这不表示没有匹配。`inputLimited` 表示输入被事件上限截断；`matchesComplete` 只描述这次选定输入的枚举完成状态，不能说明未加载的数据后缀。入口预先加载事件，外部测量的 JVM heap 会包含已加载输入；CLI 不输出原实验的 `MAXTotal/AVGTotal/MAXUsed/AVGUsed` 指标。

## 来源、适配与许可证

固定来源：

- CORE-paper `experiments`：`de8166902f664fd7ceec06ffa3c9a136825de07a`，作为实际导入的算法源码。
- CORE-paper `main`：`ceca254c48897545f182107eb9f4b568e1490a84`，用于审查和分支对照。
- CORE-paper-experiments `master`：`69c4ca1afeceefbed0682d75b1bf031599f9f785`，作为三套原始 CORE 查询与声明的来源。

导入保留 `exceptions`、`execution`（排除原 `callback`）、`parser`、`runtime` 与 `util.StringUtils`，并从上游 `edu.puc.core.*` 统一迁入本项目 `corecer.*`。原始来源路径与当前本地路径分别记录在 provenance 的 `upstream` 和 `local` 字段中。适配包括：

- 将 Java 源码集中到 `src/main/java/corecer/`，统一 package/import；这个目录同时包含引擎实现与本地运行入口。
- 将 ANTLR grammar 移至 `src/main/antlr4/CORE.g4`，由 Maven 生成 `corecer.parser` 下的 parser；使用原版本的 ANTLR 运行依赖。
- 用本项目 `corecer.util.Pair` 替换 JavaFX `Pair`。
- `BaseExecutor` 的缺省 callback 改为 null，由同步 session 注入 callback；`PartitionExecutor` 将 callback 传播给分区执行器。
- 为 label/event/global schema 与 profiler 增加重置或读取方法，使 session 可以顺序复用；执行算法与窗口边界保留上游行为。
- 本项目的 `corecer` 入口、输入适配和 JSON 指标替代原线程化 `Main` 与打印型 `MatchCallback`，匹配输出在离开 ECS 之前复制事件索引。

不导入 RMI/server/email/network 客户端、独立实验引擎、其他引擎源码、大型数据集、上游 Gradle/IDE 构建文件、混合引擎 Python driver 或历史实验结果。CORE 独立 JAR 的依赖和构建由本项目 Maven `core` profile 管理。

CORE 派生的匹配与查询编译源码沿用上游 **GPL-3.0**；许可证保存在 [core-cer-LICENSE.txt](../src/main/resources/META-INF/licenses/core-cer-LICENSE.txt)，随 JAR 放入 `META-INF/licenses/`。上游归属保存在 `META-INF/NOTICE`。其他 baseline 的来源与许可见各自文档；CORE 适配不更改其上游许可声明。

`./scripts/test-core.sh` 执行 `corecer.CoreRegressionSuite`，其中包含 `CoreParitySuite`、独立枚举 oracle 与 API/输入检查。27 个短流 fixture 的 `upstreamExpected` 由单独编译的、未经修改的上游 `experiments` Java 源码实跑得到，与本项目结果逐个比较；记录在 [parity/fixtures.json](../test-data/core-cer/parity/fixtures.json)。这覆盖上述选择策略、Kleene、过滤、投影、消费与窗口边界等具体见证；它不代表全部查询组合或完整论文性能实验均已复现。

本次验证通过 27 个上游对照案例、704 个语义/API 检查（其中 672 个为独立穷举对比）和 23 个输入/CLI 检查。三套原始查询替换 `TIMESTAMP=500` 后，36 条均通过编译；这是空输入编译检查。另用固定数据版本中三组数据各自的前 1,000 条真实事件运行 q1：股票、出租车、智能家居分别枚举 0、8、35 个匹配。此短流检查验证数据格式与运行入口，不用于性能结论。出租车输入中的未加引号地名 `Prince's Bay` 已有专门回归检查。
