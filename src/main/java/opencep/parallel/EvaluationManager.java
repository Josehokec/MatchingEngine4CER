package opencep.parallel;

import opencep.base.*;
import opencep.stream.OutputStream;

public interface EvaluationManager {
  void eval(Iterable<Event> events, OutputStream<PatternMatch> matches);

  String getStructureSummary();
}
