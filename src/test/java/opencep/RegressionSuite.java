package opencep;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import opencep.adaptive.*;
import opencep.base.*;
import opencep.condition.*;
import opencep.evaluation.*;
import opencep.misc.*;
import opencep.parallel.*;
import opencep.plan.*;
import opencep.plugin.sensors.*;
import opencep.plugin.stocks.*;
import opencep.plugin.twitter.*;
import opencep.stream.*;
import opencep.transformation.*;
import opencep.tree.*;

/** Dependency-free semantic and algorithm regression tests. Throws on every failure. */
public final class RegressionSuite {
  private static int checks;
  private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");

  private static PrimitiveEventStructure p(String type, String name) {
    return new PrimitiveEventStructure(type, name);
  }

  private static Event e(String type, int seconds, int value) {
    return new Event(type, START.plusSeconds(seconds), Map.of("value", value, "key", value % 3));
  }

  private static Event e(String type, int seconds, int value, double probability) {
    return new Event(type, START.plusSeconds(seconds), Map.of("value", value), probability);
  }

  private static Pattern pattern(PatternStructure s, int window) {
    return new Pattern(s, new TrueCondition(), Duration.ofSeconds(window));
  }

  private static List<PatternMatch> run(Pattern p, List<Event> events) {
    return run(
        List.of(p), events, new EvaluationMechanismParameters(), new ParallelExecutionParameters());
  }

  private static List<PatternMatch> run(
      List<Pattern> patterns,
      List<Event> events,
      EvaluationMechanismParameters eval,
      ParallelExecutionParameters parallel) {
    var output = new OutputStream<PatternMatch>();
    new CEP(patterns, eval, parallel, null).runEvents(events, output);
    check(output.isClosed(), "Output is closed");
    return output.snapshot();
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static void count(int expected, List<PatternMatch> matches, String label) {
    check(matches.size() == expected, label + ": expected " + expected + ", got " + matches.size());
  }

  private static void fails(Class<? extends Throwable> type, Runnable action, String label) {
    try {
      action.run();
      throw new AssertionError(label + " did not fail");
    } catch (Throwable e) {
      check(type.isInstance(e), label + ": " + e);
    }
  }

  private static Set<String> keys(List<PatternMatch> matches) {
    var out = new HashSet<String>();
    for (var m : matches) out.add(m.binding.key() + "/" + m.getPatternIds());
    return out;
  }

  private static EvaluationMechanismParameters settings(
      TreePlanBuilderTypes builder,
      OptimizerTypes optimizer,
      Duration interval,
      TreeEvaluationMechanismUpdateTypes update) {
    return new EvaluationMechanismParameters(
        new OptimizerParameters(
            optimizer,
            new TreePlanBuilderParameters(builder),
            Duration.ofSeconds(20),
            interval,
            0.2),
        new TreeStorageParameters(true, 1, true),
        update,
        MultiPatternTreePlanMergeApproaches.TREE_PLAN_SUBTREES_UNION,
        new LocalSearchParameters());
  }

  public static void main(String[] args) throws Exception {
    primitiveAndSequence();
    conditionsAndNesting();
    closures();
    negation();
    probabilities();
    policies();
    planning();
    multiPattern();
    adaptive();
    adaptiveNegation();
    parallel();
    preprocessing();
    streamsAndFormats();
    randomizedOracle();
    System.out.println("Regression suite: " + checks + " checks passed");
  }

  private static void primitiveAndSequence() {
    var primitive =
        new Pattern(
            p("A", "a"), new GreaterThanCondition(new Variable("a", "value"), 2), Duration.ZERO);
    count(
        1,
        run(primitive, List.of(e("A", 0, 3), e("A", 1, 1), e("B", 2, 9))),
        "Primitive condition");
    var seq = pattern(new SeqOperator(p("A", "a"), p("B", "b")), 2);
    count(1, run(seq, List.of(e("A", 0, 1), e("B", 2, 2))), "Inclusive window boundary");
    count(0, run(seq, List.of(e("A", 0, 1), e("B", 3, 2))), "Expired window");
    count(0, run(seq, List.of(e("B", 0, 1), e("A", 1, 2))), "Sequence order");
    count(
        1, run(seq, List.of(e("B", 0, 1), e("A", 0, 2))), "Equal timestamps use non-strict order");
    var repeated = pattern(new SeqOperator(p("A", "a"), p("A", "b")), 2);
    count(0, run(repeated, List.of(e("A", 0, 1))), "Cannot reuse a primitive event");
    count(
        1,
        run(repeated, List.of(e("A", 0, 1), e("A", 1, 2))),
        "Repeated type with distinct aliases");
    fails(
        IllegalArgumentException.class,
        () -> pattern(new AndOperator(p("A", "a"), p("B", "a")), 1),
        "Duplicate aliases");
    fails(
        IllegalArgumentException.class,
        () -> run(seq, List.of(e("A", 1, 1), e("B", 0, 2))),
        "Out-of-order timestamp validation");
  }

  private static void conditionsAndNesting() {
    var condition =
        new OrCondition(
            new GreaterThanCondition(new Variable("a", "value"), 9),
            new GreaterThanCondition(new Variable("b", "value"), 9));
    var seq =
        new Pattern(new SeqOperator(p("A", "a"), p("B", "b")), condition, Duration.ofSeconds(5));
    count(1, run(seq, List.of(e("A", 0, 1), e("B", 1, 10))), "OR waits for the other branch");
    count(0, run(seq, List.of(e("A", 0, 1), e("B", 1, 1))), "OR rejects when both branches fail");
    var nested =
        pattern(
            new SeqOperator(p("A", "a"), new AndOperator(p("B", "b"), p("C", "c")), p("D", "d")),
            5);
    count(
        1,
        run(nested, List.of(e("A", 0, 0), e("C", 1, 0), e("B", 2, 0), e("D", 3, 0))),
        "Nested AND inside SEQ");
    count(
        0,
        run(nested, List.of(e("C", 0, 0), e("A", 1, 0), e("B", 2, 0), e("D", 3, 0))),
        "Nested SEQ ordering");
    count(
        2,
        run(
            pattern(new OrOperator(p("A", "a"), p("B", "b")), 5),
            List.of(e("A", 0, 0), e("B", 1, 0))),
        "OR structure");
    var b = Binding.of("a", e("A", 0, 2));
    check(new SmallerThanCondition(1, new Variable("a", "value")).eval(b), "Constant on the left");
    check(new EqCondition(new Variable("a", "value"), 2.0).eval(b), "Mixed numeric equality");
    var nullable = new HashMap<String, Object>();
    nullable.put("value", null);
    var nullableBinding = Binding.of("a", new Event("A", START, nullable));
    check(
        new EqCondition(new Variable("a", "value"), null).eval(nullableBinding),
        "Nullable attribute equality");
    check(
        seq.getSubPattern(Set.of("a")).getPrimitiveEventNames().equals(List.of("a")),
        "Structure and condition projection");
  }

  private static void closures() {
    var events = List.of(e("A", 0, 1), e("A", 1, 2), e("A", 2, 3));
    count(
        4, run(pattern(new KleeneClosureOperator(p("A", "a"), 2, 3), 5), events), "Kleene bounds");
    count(
        3,
        run(pattern(new KleeneClosureOperator(p("A", "a"), 2, 2), 5), events),
        "Kleene max size");
    var c =
        new KCIndexCondition(
            Set.of("a"),
            x -> x.get("value"),
            (x, y) -> ((Number) x).intValue() < ((Number) y).intValue(),
            1);
    count(
        4,
        run(
            new Pattern(new KleeneClosureOperator(p("A", "a"), 1, 3), c, Duration.ofSeconds(5)),
            events),
        "Kleene offset");
    var value =
        new KCValueCondition(
            Set.of("a"),
            x -> x.get("value"),
            (x, y) -> ((Number) x).intValue() > ((Number) y).intValue(),
            1);
    count(
        3,
        run(
            new Pattern(new KleeneClosureOperator(p("A", "a")), value, Duration.ofSeconds(5)),
            events),
        "Kleene values");
    count(
        0,
        run(
            pattern(new KleeneClosureOperator(p("A", "a"), 2, 2), 1),
            List.of(e("A", 0, 1), e("A", 2, 2))),
        "Closure window is actual min/max");
    var nested =
        pattern(new KleeneClosureOperator(new SeqOperator(p("A", "a"), p("B", "b")), 1, 2), 5);
    count(
        6,
        run(nested, List.of(e("A", 0, 1), e("B", 1, 1), e("A", 2, 1), e("B", 3, 1))),
        "Nested closure constituents");
    fails(
        IllegalArgumentException.class,
        () -> new KleeneClosureOperator(p("A", "a"), 0, 2),
        "Invalid Kleene bounds");
  }

  private static void negation() {
    var bounded =
        pattern(new SeqOperator(p("A", "a"), new NegationOperator(p("N", "n")), p("B", "b")), 5);
    count(
        0,
        run(bounded, List.of(e("A", 0, 0), e("N", 1, 0), e("B", 2, 0))),
        "Bounded negative rejects");
    count(
        1,
        run(bounded, List.of(e("N", 0, 0), e("A", 1, 0), e("B", 2, 0))),
        "Negative outside sequence gap");
    var trailing = pattern(new SeqOperator(p("A", "a"), new NegationOperator(p("N", "n"))), 3);
    count(0, run(trailing, List.of(e("A", 0, 0), e("N", 3, 0))), "Negative on window boundary");
    count(1, run(trailing, List.of(e("A", 0, 0), e("N", 4, 0))), "Negative after window");
    count(1, run(trailing, List.of(e("A", 0, 0))), "Pending negatives flush at EOF");
    var conjunction = pattern(new AndOperator(p("A", "a"), new NegationOperator(p("N", "n"))), 3);
    count(
        0,
        run(conjunction, List.of(e("N", 0, 0), e("A", 2, 0), e("X", 9, 0))),
        "AND sees earlier forbidden events");
    var condition =
        new SmallerThanCondition(new Variable("a", "value"), new Variable("n", "value"));
    var conditional = new Pattern(trailing.fullStructure, condition, Duration.ofSeconds(3));
    count(1, run(conditional, List.of(e("A", 0, 4), e("N", 1, 1))), "Negative condition false");
    count(0, run(conditional, List.of(e("A", 0, 1), e("N", 1, 4))), "Negative condition true");
    var nested =
        pattern(
            new AndOperator(
                p("A", "a"),
                new NegationOperator(
                    new SeqOperator(p("N", "n"), p("B", "b"), new NegationOperator(p("X", "x"))))),
            3);
    count(
        0,
        run(
            nested,
            List.of(
                e("A", 0, 0),
                e("N", 1, 0),
                e("B", 2, 0),
                e("Z", 4, 0),
                e("Z", 5, 0),
                e("Z", 7, 0))),
        "Nested absence waits for negative subtree lookahead");
    count(
        1,
        run(nested, List.of(e("A", 0, 0), e("N", 1, 0), e("B", 2, 0), e("X", 3, 0), e("Z", 7, 0))),
        "Nested negative is invalidated by its own forbidden event");
  }

  private static void probabilities() {
    var p =
        new Pattern(
            new SeqOperator(p("A", "a"), p("B", "b")),
            null,
            Duration.ofSeconds(5),
            null,
            null,
            0.6);
    var matches = run(p, List.of(e("A", 0, 1, 0.9), e("B", 1, 1, 0.8)));
    count(1, matches, "Joint probability threshold");
    check(Math.abs(matches.get(0).probability - 0.72) < 1e-12, "Joint probability product");
    count(0, run(p, List.of(e("A", 0, 1, 0.7), e("B", 1, 1, 0.7))), "Confidence rejects");
    var neg =
        new Pattern(
            new SeqOperator(p("A", "a"), new NegationOperator(p("N", "n"))),
            null,
            Duration.ofSeconds(5),
            null,
            null,
            0.1);
    var result = run(neg, List.of(e("A", 0, 1, 0.9), e("N", 1, 1, 0.2)));
    count(1, result, "Uncertain negative");
    check(Math.abs(result.get(0).probability - 0.72) < 1e-12, "Negative absence probability");
    fails(
        IllegalArgumentException.class,
        () -> e("A", 0, 1, Double.NaN),
        "Invalid event probability");
    fails(
        IllegalArgumentException.class,
        () -> run(pattern(p("A", "a"), 5), List.of(e("A", 0, 1, 0.9))),
        "Probabilistic stream needs confidence");
  }

  private static void policies() {
    var structure = new SeqOperator(p("A", "a"), p("B", "b"));
    var events = List.of(e("A", 0, 0), e("A", 1, 0), e("B", 2, 0), e("B", 3, 0));
    count(4, run(pattern(structure, 5), events), "MATCH_ANY");
    count(
        2,
        run(
            new Pattern(
                structure,
                null,
                Duration.ofSeconds(5),
                new ConsumptionPolicy(SelectionStrategies.MATCH_SINGLE),
                null,
                null),
            events),
        "MATCH_SINGLE");
    count(
        2,
        run(
            new Pattern(
                structure,
                null,
                Duration.ofSeconds(5),
                new ConsumptionPolicy(SelectionStrategies.MATCH_NEXT),
                null,
                null),
            events),
        "MATCH_NEXT");
    var contiguous =
        new Pattern(
            structure,
            null,
            Duration.ofSeconds(5),
            new ConsumptionPolicy().withContiguous("a", "b"),
            null,
            null);
    count(
        0,
        run(contiguous, List.of(e("A", 0, 0), e("X", 1, 0), e("B", 2, 0))),
        "Contiguity counts irrelevant input");
    count(1, run(contiguous, List.of(e("A", 0, 0), e("B", 1, 0))), "Contiguous sequence");
    var freeze =
        new Pattern(
            new SeqOperator(p("A", "a"), p("B", "b"), p("C", "c")),
            null,
            Duration.ofSeconds(5),
            new ConsumptionPolicy().withFreeze("b"),
            null,
            null);
    count(
        1,
        run(freeze, List.of(e("A", 0, 0), e("B", 1, 0), e("A", 2, 0), e("B", 3, 0), e("C", 4, 0))),
        "Freeze blocks new prefix");
  }

  private static void planning() {
    var pattern = pattern(new SeqOperator(p("A", "a"), p("B", "b"), p("C", "c"), p("D", "d")), 5);
    pattern.setStatistics(
        new StatisticsSnapshot(
            new double[] {5, 2, 3, 1},
            new double[][] {{1, .2, .3, .4}, {.2, 1, .4, .2}, {.3, .4, 1, .5}, {.4, .2, .5, 1}}));
    var events = List.of(e("A", 0, 1), e("B", 1, 1), e("C", 2, 1), e("D", 3, 1));
    Set<String> expected = keys(run(pattern, events));
    double bestLeft = Double.POSITIVE_INFINITY, bushy = 0;
    for (var type : TreePlanBuilderTypes.values()) {
      var plan =
          new TreePlanBuilder(new TreePlanBuilderParameters(type))
              .buildTreePlan(pattern, pattern.getStatistics());
      check(Double.isFinite(plan.cost()), "Finite cost: " + type);
      check(
          keys(run(
                  List.of(pattern),
                  events,
                  settings(
                      type,
                      OptimizerTypes.TRIVIAL_OPTIMIZER,
                      null,
                      TreeEvaluationMechanismUpdateTypes.TRIVIAL_TREE_EVALUATION),
                  new ParallelExecutionParameters()))
              .equals(expected),
          "Plan preserves matches: " + type);
      if (type == TreePlanBuilderTypes.DYNAMIC_PROGRAMMING_BUSHY_TREE) bushy = plan.cost();
      if (type == TreePlanBuilderTypes.DYNAMIC_PROGRAMMING_LEFT_DEEP_TREE) bestLeft = plan.cost();
    }
    check(bushy <= bestLeft + 1e-10, "Bushy DP costs no more than left-deep DP");
    var builder =
        new TreePlanBuilder(
            new TreePlanBuilderParameters(
                TreePlanBuilderTypes.INVARIANT_AWARE_GREEDY_LEFT_DEEP_TREE));
    builder.buildTreePlan(pattern, pattern.getStatistics());
    check(
        !builder.getInvariants().isInvariantsViolated(pattern.getStatistics()),
        "Initial greedy invariants hold");
    var altered = new StatisticsSnapshot(new double[] {.01, 20, 30, 1000}, null);
    check(builder.getInvariants().isInvariantsViolated(altered), "Changed greedy invariants fail");
    check(
        new DeviationAwareTester(.5).isDeviated(altered, pattern.getStatistics()),
        "Deviation-aware tester");
    var arrival = new ArrivalRatesStatistics(pattern, Duration.ofSeconds(2));
    arrival.update(e("A", 0, 0));
    arrival.update(e("B", 3, 0));
    check(arrival.getStatistics()[0] == 5, "Expired arrivals decrement only observed counts");
    for (var approach : MultiPatternTreePlanMergeApproaches.values()) {
      var plan = builder.buildTreePlan(pattern, pattern.getStatistics());
      var merged =
          new TreePlanMerger(
                  approach,
                  new LocalSearchParameters(
                      LocalSearchParameters.Approach.TABU_SEARCH, 3, 3, 10, .9, 1))
              .mergeTreePlans(List.of(plan, plan));
      check(merged.size() == 2, "Merge workload size: " + approach);
    }
  }

  private static void multiPattern() {
    var first =
        new Pattern(
            new SeqOperator(p("A", "a"), p("B", "b"), p("C", "c")),
            null,
            Duration.ofSeconds(5),
            null,
            1,
            null);
    var second =
        new Pattern(
            new SeqOperator(p("A", "a"), p("B", "b"), p("D", "d")),
            null,
            Duration.ofSeconds(5),
            null,
            2,
            null);
    var output = new ArrayList<PatternMatch>();
    var mechanism =
        new TreeBasedEvaluationMechanism(
            List.of(first, second), new EvaluationMechanismParameters(), output::add);
    List.of(e("A", 0, 0), e("B", 1, 0), e("C", 2, 0), e("D", 3, 0)).forEach(mechanism::handleEvent);
    mechanism.finish();
    count(2, output, "Shared DAG detects both patterns");
    check(
        mechanism.getUniqueSharedNodeCount() < mechanism.getTotalTreeNodeCount(),
        "Shared DAG reuses physical nodes");
    var duplicate = new Pattern(first.fullStructure, null, first.window, null, 2, null);
    output.clear();
    mechanism =
        new TreeBasedEvaluationMechanism(
            List.of(first, duplicate), new EvaluationMechanismParameters(), output::add);
    List.of(e("A", 0, 0), e("B", 1, 0), e("C", 2, 0)).forEach(mechanism::handleEvent);
    mechanism.finish();
    count(1, output, "Identical roots combine pattern IDs");
    check(output.get(0).getPatternIds().equals(List.of(1, 2)), "Combined pattern IDs");
    var low =
        new Pattern(
            first.fullStructure,
            new GreaterThanCondition(new Variable("a", "value"), 5),
            first.window,
            null,
            1,
            null);
    var high =
        new Pattern(
            first.fullStructure,
            new GreaterThanCondition(new Variable("a", "value"), 10),
            first.window,
            null,
            2,
            null);
    output.clear();
    mechanism =
        new TreeBasedEvaluationMechanism(
            List.of(low, high), new EvaluationMechanismParameters(), output::add);
    List.of(e("A", 0, 7), e("B", 1, 0), e("C", 2, 0)).forEach(mechanism::handleEvent);
    mechanism.finish();
    count(1, output, "Shared nodes preserve different descendant predicates");
    check(output.get(0).getPatternIds().equals(List.of(1)), "Predicate-specific pattern ID");
  }

  private static void adaptive() {
    var pattern = pattern(new SeqOperator(p("A", "a"), p("B", "b")), 3);
    var events = new ArrayList<Event>();
    for (int i = 0; i < 30; i++) events.add(e(i % 2 == 0 ? "A" : "B", i, i));
    var baseline = keys(run(pattern, events));
    for (var update : TreeEvaluationMechanismUpdateTypes.values())
      for (var optimizer : OptimizerTypes.values()) {
        var output = new ArrayList<PatternMatch>();
        var mechanism =
            new TreeBasedEvaluationMechanism(
                List.of(pattern),
                settings(
                    TreePlanBuilderTypes.INVARIANT_AWARE_GREEDY_LEFT_DEEP_TREE,
                    optimizer,
                    Duration.ofSeconds(1),
                    update),
                output::add);
        events.forEach(mechanism::handleEvent);
        mechanism.finish();
        check(keys(output).equals(baseline), "Adaptive matches: " + update + "/" + optimizer);
        check(output.size() == baseline.size(), "Adaptive output has no duplicates");
        check(mechanism.getReoptimizationCount() > 0, "Adaptive plan actually changed");
      }
  }

  private static void adaptiveNegation() {
    var structure =
        new AndOperator(
            p("A", "a"),
            new NegationOperator(
                new SeqOperator(p("N", "n"), p("B", "b"), new NegationOperator(p("X", "x")))));
    var pattern = pattern(structure, 3);
    var events = new ArrayList<Event>();
    for (int i = 0; i < 30; i++) events.add(e(List.of("A", "N", "B", "Z", "X").get(i % 5), i, 0));
    var expected = keys(run(pattern, events));
    for (var update : TreeEvaluationMechanismUpdateTypes.values()) {
      var result =
          run(
              List.of(pattern),
              events,
              settings(
                  TreePlanBuilderTypes.GREEDY_LEFT_DEEP_TREE,
                  OptimizerTypes.TRIVIAL_OPTIMIZER,
                  Duration.ofSeconds(1),
                  update),
              new ParallelExecutionParameters());
      check(
          keys(result).equals(expected) && result.size() == expected.size(),
          "Adaptive nested negation: " + update);
    }
  }

  private static void parallel() {
    var pattern = pattern(new SeqOperator(p("A", "a"), p("B", "b")), 3);
    var events = new ArrayList<Event>();
    for (int i = 0; i < 30; i++) events.add(e(i % 2 == 0 ? "A" : "B", i, i));
    var baseline = keys(run(pattern, events));
    for (int units : List.of(1, 2, 3, 5))
      check(
          keys(run(
                  List.of(pattern),
                  events,
                  new EvaluationMechanismParameters(),
                  ParallelExecutionParameters.rip(units)))
              .equals(baseline),
          "RIP routing: " + units);
    var cube =
        ParallelExecutionParameters.hyperCube(9, Map.of("A", List.of("key"), "B", List.of("key")));
    check(
        keys(run(List.of(pattern), events, new EvaluationMechanismParameters(), cube))
            .equals(baseline),
        "Hypercube routing");
    var equal =
        new Pattern(
            pattern.fullStructure,
            new EqCondition(new Variable("a", "key"), new Variable("b", "key")),
            pattern.window);
    baseline = keys(run(equal, events));
    check(
        keys(run(
                List.of(equal),
                events,
                new EvaluationMechanismParameters(),
                ParallelExecutionParameters.groupByKey(3, "key")))
            .equals(baseline),
        "Group-by-key routing");
    check(
        Arrays.equals(HyperCubeParallelExecutionAlgorithm.calcCubicShares(10, 2), new int[] {3, 3}),
        "Balanced cube shares");
    count(
        0,
        run(
            List.of(pattern),
            List.of(),
            new EvaluationMechanismParameters(),
            ParallelExecutionParameters.rip(3)),
        "Empty parallel input");
  }

  private static void preprocessing() {
    var structure = new SeqOperator(p("A", "a"), new OrOperator(p("B", "b"), p("C", "c")));
    var patterns =
        new PatternPreprocessor(PatternPreprocessingParameters.allRules())
            .transformPatterns(List.of(pattern(structure, 3)));
    check(patterns.size() == 2, "Disjunction expands into two branches");
    var original = new AndOperator(p("A", "a"), new AndOperator(p("B", "b"), p("C", "c")));
    var transformed =
        new PatternTransformer(PatternTransformationRules.AND_AND_PATTERN)
            .transform(original)
            .get(0);
    check(((CompositeStructure) transformed).args.size() == 3, "AND flattens");
    check(original.args.size() == 2, "Preprocessing is immutable");
    var doubleNeg = new NegationOperator(new NegationOperator(p("A", "a")));
    check(
        new PatternTransformer(PatternTransformationRules.NOT_NOT_PATTERN)
            .transform(doubleNeg)
            .get(0)
            .equals(p("A", "a")),
        "Double negation");
    var deMorgan = new NegationOperator(new OrOperator(p("A", "a"), p("B", "b")));
    check(
        new PatternTransformer(PatternTransformationRules.NOT_OR_PATTERN).transform(deMorgan).get(0)
            instanceof AndOperator,
        "De Morgan");
  }

  private static void streamsAndFormats() throws Exception {
    var stream = new Stream<Integer>();
    var future = CompletableFuture.supplyAsync(stream::getItem);
    stream.addItem(7);
    check(future.get(2, TimeUnit.SECONDS) == 7, "Blocking stream wakes on input");
    stream.close();
    check(stream.getItem() == null, "EOF wakes stream");
    fails(IllegalStateException.class, () -> stream.addItem(8), "Closed stream rejects writes");
    var stocks = new MetastockDataFormatter();
    var e = new Event("GOOG,202001010900,1,2,0,1,10,0.8", stocks);
    check(
        e.type.equals("GOOG") && e.timestamp.equals(Instant.parse("2020-01-01T09:00:00Z")),
        "Metastock formatter");
    check(e.probability == .8, "Optional Metastock probability");
    var sensor =
        new Event("Accelerometer,01/02/2020 03:04:05,0.1,1,2,3", new SensorsDataFormatter());
    check(sensor.payload.get("AccZ").equals(3L), "Sensor fields");
    var tweet = new TweetDataFormatter();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("created_at", "Wed Oct 10 20:19:24 +0000 2018");
    check(
        tweet.getEventTimestamp(payload).equals(Instant.parse("2018-10-10T20:19:24Z")),
        "Tweet timestamp");
    check(Json.parse("{\"a\": [1,true,null,\"x\\n\"]}") instanceof Map<?, ?>, "JSON parser");
    fails(IllegalArgumentException.class, () -> Json.parse("01"), "JSON rejects malformed numbers");
    var array = new NDArray<>(List.of(0, 1, 2, 3, 4, 5), 2, 3);
    check(array.get(1, 2) == 5 && array.reshape(-1).size() == 6, "NDArray shape and indexing");
    var column = (NDArray<?>) array.slice(new NDArray.Slice(), 1);
    check(column.toList().equals(List.of(1, 4)), "NDArray axis slices");
    check(array.slice(-1, -1).equals(5), "NDArray negative indices");
    var nestedArray = NDArray.fromNested(List.of(List.of(1, 2), List.of(3, 4)));
    check(
        Arrays.equals(nestedArray.shape(), new int[] {2, 2}) && nestedArray.get(1, 0).equals(3),
        "NDArray nested initialization");
    var estimated =
        LegacyStatistics.estimate(
            pattern(new SeqOperator(p("A", "a"), p("B", "b")), 5),
            List.of(e("A", 0, 1), e("B", 2, 2)));
    check(
        estimated.rate(0) == .5 && estimated.selectivity(0, 1) == 1,
        "Offline statistics estimates");
    var storage = new SortedPatternMatchStorage(c -> c.events.get(0).payload.get("value"), 1);
    var x = Candidate.leaf("a", e("A", 0, 3));
    storage.add(x);
    storage.add(Candidate.leaf("a", e("A", 0, 1)));
    storage.add(Candidate.leaf("a", e("A", 0, 3)));
    check(storage.get(3, RelopTypes.EQUAL).size() == 2, "Sorted equality range");
    check(storage.get(2, RelopTypes.GREATER).size() == 2, "Sorted inequality range");
    Path output = Path.of("target/regression-matches.txt");
    try (var input = new FileInputStream("test-data/stocks-demo.csv")) {
      new CEP(pattern(new SeqOperator(p("GOOG", "a"), p("GOOG", "b"), p("GOOG", "c")), 180))
          .run(input, new FileOutputStream<PatternMatch>(output), stocks);
    }
    check(Files.size(output) > 0, "File input/output");
  }

  private static void randomizedOracle() {
    var random = new Random(1907);
    for (int trial = 0; trial < 120; trial++) {
      boolean seq = trial % 2 == 0;
      var events = new ArrayList<Event>();
      for (int i = 0; i < 10; i++)
        events.add(e(List.of("A", "B", "C").get(random.nextInt(3)), i / 2, random.nextInt(10)));
      var structure =
          seq
              ? new SeqOperator(p("A", "a"), p("B", "b"), p("C", "c"))
              : new AndOperator(p("A", "a"), p("B", "b"), p("C", "c"));
      var pattern =
          new Pattern(
              structure,
              new SmallerThanCondition(new Variable("a", "value"), new Variable("c", "value")),
              Duration.ofSeconds(2));
      var expected = new HashSet<String>();
      for (var a : events)
        for (var b : events)
          for (var c : events) {
            if (!a.type.equals("A") || !b.type.equals("B") || !c.type.equals("C")) continue;
            if (((Number) a.payload.get("value")).intValue()
                >= ((Number) c.payload.get("value")).intValue()) continue;
            Instant
                first =
                    List.of(a, b, c).stream()
                        .map(v -> v.timestamp)
                        .min(Comparator.naturalOrder())
                        .orElseThrow(),
                last =
                    List.of(a, b, c).stream()
                        .map(v -> v.timestamp)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
            if (Duration.between(first, last).toSeconds() > 2
                || (seq && (a.timestamp.isAfter(b.timestamp) || b.timestamp.isAfter(c.timestamp))))
              continue;
            expected.add(
                new Binding(Map.of("a", List.of(a), "b", List.of(b), "c", List.of(c))).key()
                    + "/[]");
          }
      var actual = run(pattern, events);
      check(keys(actual).equals(expected), "Independent exhaustive oracle trial " + trial);
      check(actual.size() == expected.size(), "Oracle duplicate count " + trial);
    }
  }
}
