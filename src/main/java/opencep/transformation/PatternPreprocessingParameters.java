package opencep.transformation;

import java.util.*;

public record PatternPreprocessingParameters(List<PatternTransformationRules> transformationRules) {
  public PatternPreprocessingParameters() {
    this(List.of());
  }

  public PatternPreprocessingParameters {
    transformationRules = List.copyOf(transformationRules);
  }

  public static PatternPreprocessingParameters allRules() {
    return new PatternPreprocessingParameters(
        List.of(
            PatternTransformationRules.AND_AND_PATTERN,
            PatternTransformationRules.NOT_OR_PATTERN,
            PatternTransformationRules.NOT_AND_PATTERN,
            PatternTransformationRules.TOPMOST_OR_PATTERN,
            PatternTransformationRules.INNER_OR_PATTERN,
            PatternTransformationRules.NOT_NOT_PATTERN));
  }
}
