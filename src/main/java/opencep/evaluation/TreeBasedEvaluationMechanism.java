package opencep.evaluation;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import opencep.adaptive.*;
import opencep.base.*;
import opencep.plan.*;
import opencep.tree.*;

public final class TreeBasedEvaluationMechanism implements EvaluationMechanism, AutoCloseable {
  private final List<Pattern> patterns;
  private final EvaluationMechanismParameters parameters;
  private final Consumer<PatternMatch> output;
  private final List<Tree> trees = new ArrayList<>();
  private final List<Optimizer> optimizers = new ArrayList<>();
  private final List<StatisticsCollector> collectors = new ArrayList<>();
  private final List<Instant> refresh = new ArrayList<>();
  private final List<Shadow> shadows = new ArrayList<>();
  private final Map<String, PatternMatch> batch = new LinkedHashMap<>();
  private NodePool nodePool;
  private Instant latest;
  private int reoptimizations;
  private boolean replaying;

  private record Shadow(Tree tree, Instant started, Duration window, Runnable activate) {}

  public TreeBasedEvaluationMechanism(
      List<Pattern> patterns,
      EvaluationMechanismParameters parameters,
      Consumer<PatternMatch> output) {
    this.patterns = List.copyOf(patterns);
    this.parameters = parameters;
    this.output = output;
    var plans = new ArrayList<TreePlan>();
    for (var p : patterns) {
      var optimizer = new Optimizer(parameters.optimizer());
      optimizers.add(optimizer);
      plans.add(optimizer.buildInitialPlan(p));
    }
    plans =
        new ArrayList<>(
            new TreePlanMerger(parameters.mergeApproach(), parameters.localSearch())
                .mergeTreePlans(plans));
    NodePool pool =
        parameters.optimizer().isAdaptivityEnabled()
                || parameters.mergeApproach() == MultiPatternTreePlanMergeApproaches.NONE
            ? null
            : new NodePool(
                parameters.mergeApproach()
                    == MultiPatternTreePlanMergeApproaches.TREE_PLAN_TRIVIAL_SHARING_LEAVES);
    nodePool = pool;
    for (var plan : plans) {
      trees.add(new Tree(plan, parameters.storage(), this::emit, pool));
      refresh.add(null);
      shadows.add(null);
      collectors.add(
          parameters.optimizer().isAdaptivityEnabled()
              ? new StatisticsCollector(plan.pattern(), parameters.optimizer().statisticsWindow())
              : null);
    }
  }

  private void emit(PatternMatch match) {
    if (replaying) return;
    if (patterns.size() == 1) {
      output.accept(match);
      return;
    }
    String key =
        match.events.stream()
                .map(
                    e ->
                        e.primitiveEvents().stream()
                            .map(v -> Long.toString(v.index))
                            .toList()
                            .toString())
                .sorted()
                .toList()
                .toString()
            + "/"
            + match.probability;
    var existing = batch.get(key);
    if (existing == null) batch.put(key, match);
    else match.getPatternIds().forEach(existing::addPatternId);
  }

  private void flushBatch() {
    batch.values().forEach(output);
    batch.clear();
  }

  public void handleEvent(Event event) {
    Event.CURRENT_STREAM_INDEX.set(event.index);
    if (latest != null && event.timestamp.isBefore(latest))
      throw new IllegalArgumentException(
          "Input timestamps must be nondecreasing: " + event.timestamp + " < " + latest);
    latest = event.timestamp;
    for (int i = 0; i < trees.size(); i++) {
      var collector = collectors.get(i);
      if (collector != null) {
        collector.handleEvent(event);
        var last = refresh.get(i);
        if (shadows.get(i) == null
            && (last == null
                || Duration.between(last, event.timestamp)
                        .compareTo(parameters.optimizer().updateInterval())
                    > 0)) {
          var stats = collector.getStatistics();
          if (optimizers.get(i).shouldOptimize(stats))
            update(i, optimizers.get(i).buildNewPlan(patterns.get(i), stats), event.timestamp);
          refresh.set(i, event.timestamp);
        }
      }
      trees.get(i).handleEvent(event);
      var shadow = shadows.get(i);
      if (shadow != null) {
        shadow.tree.handleEvent(event);
        if (event.timestamp.isAfter(shadow.started.plus(shadow.window))) {
          trees.set(i, shadow.tree);
          shadow.activate.run();
          shadows.set(i, null);
        }
      }
    }
    flushBatch();
  }

  private void update(int index, TreePlan plan, Instant timestamp) {
    reoptimizations++;
    if (parameters.updateType()
        == TreeEvaluationMechanismUpdateTypes.SIMULTANEOUS_TREE_EVALUATION) {
      // A shadow tree warms up for one full pattern window. Its output is gated until promotion.
      var active = new java.util.concurrent.atomic.AtomicBoolean();
      Tree shadow =
          new Tree(
              plan,
              parameters.storage(),
              m -> {
                if (active.get()) emit(m);
              });
      shadows.set(
          index,
          new Shadow(
              shadow,
              timestamp,
              parameters.storage().legacySemantics()
                  ? patterns.get(index).window
                  : patterns
                      .get(index)
                      .window
                      .multipliedBy(NegationNode.maxDelay(plan.root()) + 1L),
              () -> active.set(true)));
    } else {
      var replacement = new Tree(plan, parameters.storage(), this::emit);
      replaying = true;
      try {
        trees.get(index).getRetainedEvents().forEach(replacement::handleEvent);
      } finally {
        replaying = false;
      }
      trees.set(index, replacement);
    }
  }

  public void finish() {
    for (var tree : trees) tree.finish();
    flushBatch();
    close();
  }

  public void close() {
    for (int i = 0; i < collectors.size(); i++)
      if (collectors.get(i) != null) collectors.get(i).detach(patterns.get(i));
  }

  public int getUniqueSharedNodeCount() {
    return nodePool == null ? 0 : nodePool.size();
  }

  public int getTotalTreeNodeCount() {
    return trees.stream().mapToInt(Tree::getNodeCount).sum();
  }

  public int getReoptimizationCount() {
    return reoptimizations;
  }

  public String getStructureSummary() {
    return trees.stream().map(Tree::getStructureSummary).toList().toString();
  }
}
