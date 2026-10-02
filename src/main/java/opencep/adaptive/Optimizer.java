package opencep.adaptive;

import opencep.base.Pattern;
import opencep.plan.*;

public final class Optimizer {
  private final OptimizerParameters parameters;
  private StatisticsSnapshot previous;
  private Invariants invariants;

  public Optimizer(OptimizerParameters parameters) {
    this.parameters = parameters;
  }

  public boolean shouldOptimize(StatisticsSnapshot stats) {
    return switch (parameters.type()) {
      case TRIVIAL_OPTIMIZER -> true;
      case STATISTICS_DEVIATION_AWARE_OPTIMIZER -> new DeviationAwareTester(
              parameters.deviationThreshold())
          .isDeviated(stats, previous);
      case INVARIANTS_AWARE_OPTIMIZER -> invariants == null
          || invariants.isInvariantsViolated(stats);
    };
  }

  public TreePlan buildInitialPlan(Pattern pattern) {
    if (pattern.getStatistics() == null) {
      previous = StatisticsSnapshot.defaults(pattern.countPrimitiveEvents());
      return new TreePlanBuilder(new TreePlanBuilderParameters()).buildTreePlan(pattern, previous);
    }
    return buildNewPlan(pattern, pattern.getStatistics());
  }

  public TreePlan buildNewPlan(Pattern pattern, StatisticsSnapshot stats) {
    var builder = new TreePlanBuilder(parameters.treePlanParameters());
    var plan = builder.buildTreePlan(pattern, stats);
    invariants = builder.getInvariants();
    previous = stats;
    return plan;
  }
}
