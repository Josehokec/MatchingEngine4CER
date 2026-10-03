# CORE short-stream parity fixtures

`fixtures.json` contains 27 small cases. Each case records a CORE query, a short
event-type stream, optional attribute/timestamp overrides, and sorted zero-based
match indices. The declarations are shared by the cases. Callbacks copy every
match immediately because the upstream iterator reuses `ComplexEvent` objects.

- `expected`: independently derived by hand from the short stream.
- `upstreamExpected`: captured by running the pure-upstream-API
  `corecer.CoreParitySuite` harness against a separate build of the original
  source. The current harness imports the locally consolidated `corecer.*`
  engine packages; its original-source capture version must use `edu.puc.core.*`.
- Source: [CORE-paper experiments branch](https://github.com/CORE-cer/CORE-paper/tree/de8166902f664fd7ceec06ffa3c9a136825de07a),
  commit `de8166902f664fd7ceec06ffa3c9a136825de07a`.
- Original sources were built without edits, using ANTLR 4.7, JSON 20190722,
  JavaFX base 12, Commons CLI 1.3.1, JavaMail 1.4.1 and Commons Validator 1.6,
  matching that branch's `build.gradle`. No upstream jar is retained here.

All 27 independently derived outputs agreed with the original executable.
The normal regression runner compares the current implementation with both
columns. To recapture against a separately compiled original classpath, first
copy `src/test/java/corecer/CoreParitySuite.java` into a temporary directory.
In that temporary copy only, replace the `corecer.execution`, `corecer.parser`,
and `corecer.runtime` package prefixes with `edu.puc.core.execution`,
`edu.puc.core.parser`, and `edu.puc.core.runtime`. Keep the harness's own
`package corecer` declaration and class name unchanged. Compile this adapted
temporary harness against the original source build and its dependencies, then
run `corecer.CoreParitySuite` with `test-data/core-cer/parity/fixtures.json` and
`--capture`, using only the original classes and the temporary harness on its
classpath. Do not alter the original engine source, mix in the local CORE JAR,
or compile the current local harness unchanged against the original package
names. This capture adaptation does not change the fixture queries or inputs.

The fixtures explicitly preserve these upstream semantics:

- Window bounds are exclusive: end minus start must be less than the span.
- Default consumption is `ANY`. Enumeration cases use `CONSUME BY NONE`.
- Without `WITHIN`, `SimpleExecutor` starts only at the first input position.
  Windowed execution starts at every position. A separate fixture records this
  unwindowed behavior.
- A projection that omits the final pattern event does not trigger a match.
  The suffix-projection fixture retains the final event.

`CoreRegressionSuite` additionally checks 672 independently enumerated seeded
streams for `ALL`, `NEXT`, `LAST`, and `MAX`, with sequences and one-or-more
repetition; detached API results, multiple query factories, partitioning,
consumption, enumeration modes, session lifecycle, and invalid input. Partition
tests cover the local callback propagation adapter; the original executor did
not forward a custom callback to its partition children. The input/CLI suite
checks portable file parsing, timestamp merge order, descriptors, JSONL output,
and input diagnostics. Everything runs without JUnit or an upstream checkout.
