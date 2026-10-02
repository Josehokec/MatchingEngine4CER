package opencep.adaptive;

import java.time.Duration;
import opencep.plan.TreePlanBuilderParameters;

public record OptimizerParameters(
    OptimizerTypes type,
    TreePlanBuilderParameters treePlanParameters,
    Duration statisticsWindow,
    Duration updateInterval,
    double deviationThreshold) {
  public OptimizerParameters() {
    this(
        OptimizerTypes.STATISTICS_DEVIATION_AWARE_OPTIMIZER,
        new TreePlanBuilderParameters(),
        Duration.ofHours(1),
        null,
        0.5);
  }

  public OptimizerParameters {
    if (type == null
        || treePlanParameters == null
        || statisticsWindow == null
        || statisticsWindow.isNegative()
        || statisticsWindow.isZero()
        || deviationThreshold < 0
        || !Double.isFinite(deviationThreshold)
        || (updateInterval != null && updateInterval.isNegative()))
      throw new IllegalArgumentException("Invalid optimizer parameters");
  }

  public boolean isAdaptivityEnabled() {
    return updateInterval != null;
  }
}
