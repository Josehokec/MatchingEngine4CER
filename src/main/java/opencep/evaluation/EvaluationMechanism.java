package opencep.evaluation;

import opencep.base.Event;

public interface EvaluationMechanism {
  void handleEvent(Event event);

  void finish();

  String getStructureSummary();
}
