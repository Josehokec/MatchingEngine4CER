# Migration and source mapping

Upstream: [ilya-kolchinsky/OpenCEP](https://github.com/ilya-kolchinsky/OpenCEP), commit [`e320ad874dd82e41cf89cea4459a2ca62d71ca84`](https://github.com/ilya-kolchinsky/OpenCEP/tree/e320ad874dd82e41cf89cea4459a2ca62d71ca84), downloaded on 2026-10-02. The inspected snapshot contains 101 runtime Python modules and 21 Python test modules, totaling 16,494 lines including comments and tests. Source attribution remains with the upstream project and its contributors.

The Java implementation consolidates factories, class hierarchies, and algorithm variants rather than retaining every Python filename. Java source is under `src/main/java`; tests are under `src/test/java`. No Python interpreter or Python source is needed to build or run this project.

| Upstream component | Java implementation |
| --- | --- |
| `CEP.py` | `opencep.CEP` |
| `base/*` | `opencep.base`: immutable pattern structures, patterns, events, aggregate events, bindings, matches, formatters, classifiers |
| `condition/*` | `opencep.condition`: atomic/composite predicates, variables, relations, closure predicates |
| `stream/*` | `opencep.stream`: blocking streams, lazy file input, streaming file output |
| `tree/nodes/*`, `tree/Tree.py`, `tree/MultiPatternTree.py` | `opencep.tree`: nodes, candidates, logical constraints, trees, shared node registry; workload output aggregation in `TreeBasedEvaluationMechanism` |
| `tree/PatternMatchStorage.py` | `PatternMatchStorage`, `SortedPatternMatchStorage`, `UnsortedPatternMatchStorage`, `JoinIndex`, `TreeStorageParameters` |
| `plan/TreePlan*`, left-deep and bushy builders | `opencep.plan`: immutable physical plans, parameter records, planner factory, tree planner, intermediate-results cost model |
| `plan/IterativeImprovement.py` | `IterativeImprovement`: swap and three-element cycle moves, reproducible random seeds |
| `plan/invariant/*` | Greedy decision comparisons and ZStream split comparisons recorded in `Invariants` during planning |
| `plan/negation/*` | Naive, statistics-based, and lowest-position placement in `TreePlanBuilder`; negative joins in `NegationNode` |
| `plan/multi/*`, local-search graph/state/search modules | `TreePlanMerger`, `NodePool`, `LocalSearchParameters`: workload states, join-order neighborhoods, shared cost estimates, tabu and annealing searches |
| `adaptive/statistics/*` | `StatisticsSnapshot`, arrival-rate and selectivity collectors, `StatisticsCollector` |
| `adaptive/optimizer/*` | `Optimizer`, `OptimizerParameters`, `OptimizerTypes`, `DeviationAwareTester` |
| `evaluation/*`, `tree/evaluation/*` | `EvaluationMechanism`, `EvaluationMechanismParameters`, `TreeBasedEvaluationMechanism`: replay or shadow-tree updates |
| `parallel/manager/*`, factories and parameter modules | `EvaluationManager`, sequential/data-parallel managers, parameter records and enums |
| `parallel/platform/*` | JDK executors, bounded blocking mailboxes, interruption, worker failure propagation, synchronized stream writes |
| `parallel/data_parallel/*` | Group-by-key, RIP and hypercube routing classes with match ownership filters |
| `transformation/*` | Immutable preprocessing and recursive transformers for flattening, disjunction distribution/splitting, De Morgan rules, and double negation |
| `plugin/stocks/*` | `opencep.plugin.stocks` |
| `plugin/sensors/*` | `opencep.plugin.sensors`, including deterministic sample generation |
| `plugin/twitter/*` | `opencep.plugin.twitter`: legacy tweet formatter, runtime bearer credentials, configurable HTTP JSON-line input |
| `misc/*` | `opencep.misc`: defaults, policies, selection strategies, collection utilities, offline statistics, multidimensional arrays; JSON codec added for tweet records and fixtures |
| Python test/benchmark harnesses | Java regression suite, portable upstream fixtures, and parity suite; performance benchmark scripts are not retained as Python programs |

## Java API choices

* `timedelta` becomes `Duration`; timestamps become `Instant`. Stock and sensor timestamps lack an explicit zone and are interpreted as UTC for consistent relative comparisons.
* Event payloads are `Map<String,Object>`. Event and pattern structures are immutable. A closure binding contains event lists; closure output contains `AggregatedEvent` objects.
* Primitive alias names must be unique within a simultaneously evaluated branch. OR alternatives may reuse aliases.
* `PatternMatch.getPatternIds()` identifies matched patterns in workloads. Equivalent output event groups can carry multiple pattern IDs. A match's `binding` uses the aliases of the first reporting pattern.
* API names use Java camelCase. Physical structure summaries are strings rather than Python tuples. Function predicates replace Python callable arguments; configuration records replace mutable Python parameter objects.
* Numeric comparisons handle differing Java numeric types. Numeric payload values and null equality are supported. Invalid probabilities and descending input timestamps produce explicit errors.
* Factory modules with only one supported implementation are consolidated into constructors or parameter records. The source's optional external clients are replaced with JDK facilities.

## Evaluation profiles

The default mode and historical compatibility mode use the same Java planner and streaming node implementation. Compatibility is a runtime setting, not a call into Python or a lookup of recorded answers.

| Concern | Default mode | Historical compatibility mode |
| --- | --- | --- |
| Aggregate timestamps | Actual minimum and maximum across constituents | Endpoint timestamps, reproducing the Python assumption that constituents are sorted |
| Aggregate identity during joins | Unique identities, primitive reuse checks | Historical aggregate/next-primitive identity comparison |
| Composite closure constituents | Supports repeated constituents, checks actual window bounds, suppresses duplicate bindings | Reproduces historical powerset propagation and duplicate behavior |
| Contiguity | Every specified adjacent pair | Historical offset-based indexing |
| Nested negative evidence | Waits for nested negative lookahead; retains necessary history; flushes nested pending nodes | Historical per-node cleanup and root-negative flushing |
| Uncertain future negatives | Multiplies by the probability of absence | Historical rejection of pending matches when a matching future negative arrives |

Compatibility-mode matching is validated on the saved corpus below. Planner tree shapes, tie breaking, local-search trajectories, and wall-clock performance are Java-specific. Matching a corpus does not establish exhaustive equivalence for every unsupported or untested Python configuration.

## Supported combinations and limits

* Inputs must be ordered by timestamp. Window endpoints and equal timestamps are inclusive.
* Adaptivity uses a positive statistics window and a non-null update interval; it operates in sequential mode. Replaying retained events suppresses previously reported matches. Shadow trees warm up before their outputs are activated; default-mode nested negation extends that warmup as necessary.
* Physical sharing requires compatible aliases, conditions throughout the subtree, windows, and confidence settings. Trees using limiting consumption, freeze, or contiguity policies keep separate state. Adaptive trees are rebuilt independently.
* Data-parallel execution uses independent workers and bounded queues. Adaptive statistics, MATCH_SINGLE/MATCH_NEXT, and freeze policies require sequential mode and are rejected when combined with data parallelism. Contiguity constraints use global indexes referring to the whole input, even when routing replicates events.
* Group-by-key routing requires every contributing event to have a numeric routing key and the pattern to require equal keys. Hypercube routing requires distinct primitive event types in each pattern, numeric configured attributes, and uses at most the requested worker count. RIP uses overlapping time intervals and the maximum workload window; its interval multiplier must be at least one.
* Subset dynamic programming is limited to 20 operands per composite operator. Greedy and interval-based planners are available for larger structures. Unbounded closure enumerates exponentially many constituent combinations, as in the Python algorithm; explicit closure limits control this work.
* Every evaluated SEQ/AND needs a positive operand. A standalone NOT is not evaluated as a stream match. Preprocess double negation and disjunction rules when a structure requires those transformations.
* Tweet parsing expects the legacy OpenCEP tweet field schema. `TwitterInputStream` accepts an explicit HTTP endpoint returning that schema as JSON lines; it is not a drop-in replacement for the old Tweepy listener or for a differently shaped API response. Credentials come from `TWITTER_BEARER_TOKEN` or a constructor argument. The default stock demo and tests make no external service calls.

## Validation

`./scripts/test.sh` builds and executes three standalone programs. Checks throw exceptions on failure and cause a nonzero exit status; they do not rely on JVM assertions alone.

* `RegressionSuite` covers independent exhaustive/randomized matching, window boundaries, conditions and projections, nested operators, closure predicates, negative evidence, confidence calculations, all selection policies, all ten planners, statistics, both adaptive update methods (including nested negation), physical DAG sharing, parallel ownership, preprocessing, storage ranges, array slicing, file I/O, and formatter behavior.
* `UpstreamParitySuite` reconstructs patterns and Python lambda expressions from portable data and evaluates them through Java compatibility mode. Expected event memberships, closure groups, multiplicities, pattern IDs, and probabilities come from the pinned Python engine.
* The parity suite runs again with raw records parsed during evaluation, checking that event indexing and aggregate identities also reproduce the streaming input path.

The corpus contains 126 distinct pattern/input configurations drawn from BasicTests, NestedTests, NegationTests, KC_tests, PolicyTests, EventProbabilityTests, MultiPattern_tests, TreeConstructionTests, EvaluationTests, ParallelTests, and LocalSearchTests. It includes 98 nonempty reference results and 8,767 reference match objects. Each input is limited to its first 180 rows; shorter custom and closure files are retained in full. Reference execution uses the trivial sequential Python evaluator to isolate matching semantics. Java planner/adaptation/routing checks are separate regressions, not claims that every original optimization benchmark was translated literally.

The fixture schema stores pattern structure, conditions/lambda ASTs, policies, statistics when present, raw records, formatter type, and normalized Python event positions. The test-only lambda interpreter accepts a fixed expression vocabulary and performs no arbitrary code execution.

Fixture: `test-data/upstream-cases.json`.
SHA-256: `71d89febaa385ab3e5f4f8f57f7723c37a29f3dba3b92a951153cda07fdb86ca`.

Final checks: 554 Java regression assertions; 126/126 Python parity fixtures passed with Java events and 126/126 with raw streaming records. The JDK build, executable demo, file-output example, and offline Maven packaging were verified locally with Java 17.0.6. The project does not include Python code or require network access for its JDK-only build and tests.
