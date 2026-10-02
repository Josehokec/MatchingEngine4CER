package opencep.plan;

import java.util.Objects;

public final class TreePlanBuilderParameters {
  public final TreePlanBuilderTypes builderType;
  public final NegationAlgorithmTypes negationAlgorithm;
  public final int stepLimit;
  public final long randomSeed;
  public final boolean circleMoves, randomInitialOrder;

  public TreePlanBuilderParameters() {
    this(TreePlanBuilderTypes.TRIVIAL_LEFT_DEEP_TREE);
  }

  public TreePlanBuilderParameters(TreePlanBuilderTypes type) {
    this(type, NegationAlgorithmTypes.NAIVE_NEGATION_ALGORITHM, 100, 0, false, false);
  }

  public TreePlanBuilderParameters(
      TreePlanBuilderTypes type,
      NegationAlgorithmTypes negation,
      int steps,
      long seed,
      boolean circle,
      boolean randomInitial) {
    if (steps < 0) throw new IllegalArgumentException("Negative step limit");
    builderType = Objects.requireNonNull(type);
    negationAlgorithm = Objects.requireNonNull(negation);
    stepLimit = steps;
    randomSeed = seed;
    circleMoves = circle;
    randomInitialOrder = randomInitial;
  }
}
