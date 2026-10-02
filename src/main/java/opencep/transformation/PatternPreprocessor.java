package opencep.transformation;

import java.util.*;
import opencep.base.*;

public final class PatternPreprocessor {
  private final PatternPreprocessingParameters params;

  public PatternPreprocessor(PatternPreprocessingParameters params) {
    this.params = params == null ? new PatternPreprocessingParameters() : params;
  }

  public List<Pattern> transformPatterns(List<Pattern> patterns) {
    var result = new ArrayList<Pattern>();
    for (var original : patterns) {
      List<PatternStructure> structures = List.of(original.fullStructure);
      for (int pass = 0; pass < 100; pass++) {
        var before = structures;
        for (var rule : params.transformationRules()) {
          var transformer = new PatternTransformer(rule);
          structures =
              structures.stream()
                  .flatMap(s -> transformer.transform(s).stream())
                  .distinct()
                  .toList();
          if (structures.size() > 10000)
            throw new IllegalArgumentException(
                "Preprocessing expanded to more than 10000 branches");
        }
        if (structures.equals(before)) break;
        if (pass == 99) throw new IllegalStateException("Pattern transformations did not converge");
      }
      for (var s : structures) {
        if (s.equals(original.fullStructure)) {
          result.add(original);
          continue;
        }
        var p =
            new Pattern(
                s,
                original.condition.getConditionProjection(new HashSet<>(s.getAllEventNames())),
                original.window,
                original.consumptionPolicy,
                original.id,
                original.confidence);
        if (original.getStatistics() != null)
          p.setStatistics(
              original
                  .getStatistics()
                  .project(original.getPrimitiveEventNames(), p.getPrimitiveEventNames()));
        result.add(p);
      }
    }
    return List.copyOf(result);
  }
}
