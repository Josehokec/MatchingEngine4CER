package opencep.misc;

import java.time.Duration;
import opencep.adaptive.*;
import opencep.plan.*;
import opencep.tree.*;

/** Corresponding upstream defaults; records expose per-engine overrides. */
public final class DefaultConfig {
  private DefaultConfig() {}

  public static final TreePlanBuilderTypes DEFAULT_TREE_PLAN_BUILDER =
      TreePlanBuilderTypes.TRIVIAL_LEFT_DEEP_TREE;
  public static final MultiPatternTreePlanMergeApproaches DEFAULT_TREE_PLAN_MERGE =
      MultiPatternTreePlanMergeApproaches.TREE_PLAN_SUBTREES_UNION;
  public static final SelectionStrategies PRIMARY_SELECTION_STRATEGY =
      SelectionStrategies.MATCH_ANY;
  public static final boolean SHOULD_SORT_STORAGE = false;
  public static final int CLEANUP_INTERVAL = 10;
  public static final boolean PRIORITIZE_SORTING_BY_TIMESTAMP = true;
  public static final NegationAlgorithmTypes DEFAULT_NEGATION_ALGORITHM =
      NegationAlgorithmTypes.NAIVE_NEGATION_ALGORITHM;
  public static final OptimizerTypes DEFAULT_OPTIMIZER_TYPE =
      OptimizerTypes.STATISTICS_DEVIATION_AWARE_OPTIMIZER;
  public static final Duration STATISTICS_TIME_WINDOW = Duration.ofHours(1);
  public static final Duration STATISTICS_UPDATES_WAIT_TIME = null;
  public static final double DEVIATION_OPTIMIZER_THRESHOLD = 0.5;
  public static final TreeEvaluationMechanismUpdateTypes DEFAULT_TREE_UPDATE_TYPE =
      TreeEvaluationMechanismUpdateTypes.TRIVIAL_TREE_EVALUATION;
  public static final int DEFAULT_PARALLEL_UNITS_NUMBER = 1;
  public static final double DEFAULT_PARALLEL_MULTIPLE = 12;
}
