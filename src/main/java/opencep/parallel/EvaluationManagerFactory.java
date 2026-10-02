package opencep.parallel;

import java.util.List;
import opencep.base.Pattern;
import opencep.evaluation.EvaluationMechanismParameters;

public final class EvaluationManagerFactory {
  private EvaluationManagerFactory() {}

  public static EvaluationManager createEvaluationManager(
      List<Pattern> patterns,
      EvaluationMechanismParameters evaluation,
      ParallelExecutionParameters parallel) {
    return parallel.mode() == ParallelExecutionModes.SEQUENTIAL
        ? new SequentialEvaluationManager(patterns, evaluation)
        : new DataParallelEvaluationManager(patterns, evaluation, parallel);
  }
}
