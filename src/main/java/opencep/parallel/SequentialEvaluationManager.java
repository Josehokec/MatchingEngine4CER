package opencep.parallel;

import java.util.*;
import opencep.base.*;
import opencep.evaluation.*;
import opencep.stream.OutputStream;

public final class SequentialEvaluationManager implements EvaluationManager {
  private final List<Pattern> patterns;
  private final EvaluationMechanismParameters parameters;
  private TreeBasedEvaluationMechanism mechanism;

  public SequentialEvaluationManager(
      List<Pattern> patterns, EvaluationMechanismParameters parameters) {
    this.patterns = List.copyOf(patterns);
    this.parameters = parameters;
  }

  public void eval(Iterable<Event> events, OutputStream<PatternMatch> matches) {
    mechanism = new TreeBasedEvaluationMechanism(patterns, parameters, matches::addItem);
    try {
      for (var event : events) mechanism.handleEvent(event);
      mechanism.finish();
    } finally {
      mechanism.close();
      matches.close();
    }
  }

  public String getStructureSummary() {
    return mechanism == null ? patterns.toString() : mechanism.getStructureSummary();
  }
}
