package opencep.evaluation;

import opencep.adaptive.OptimizerParameters;
import opencep.plan.*;
import opencep.tree.*;

public record EvaluationMechanismParameters(
    OptimizerParameters optimizer,
    TreeStorageParameters storage,
    TreeEvaluationMechanismUpdateTypes updateType,
    MultiPatternTreePlanMergeApproaches mergeApproach,
    LocalSearchParameters localSearch) {
  public EvaluationMechanismParameters() {
    this(
        new OptimizerParameters(),
        new TreeStorageParameters(),
        TreeEvaluationMechanismUpdateTypes.TRIVIAL_TREE_EVALUATION,
        MultiPatternTreePlanMergeApproaches.TREE_PLAN_SUBTREES_UNION,
        new LocalSearchParameters());
  }

  public EvaluationMechanismParameters withLegacySemantics() {
    return new EvaluationMechanismParameters(
        optimizer, storage.withLegacySemantics(), updateType, mergeApproach, localSearch);
  }

  public EvaluationMechanismParameters {
    if (optimizer == null
        || storage == null
        || updateType == null
        || mergeApproach == null
        || localSearch == null) throw new IllegalArgumentException("Missing evaluation settings");
  }
}
