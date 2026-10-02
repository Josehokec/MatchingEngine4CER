package opencep.plugin.twitter;

import java.util.Map;
import opencep.base.EventTypeClassifier;

public final class DummyTwitterEventTypeClassifier implements EventTypeClassifier {
  public String getEventType(Map<String, Object> payload) {
    return "Tweet";
  }
}
