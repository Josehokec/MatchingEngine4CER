package opencep.plugin.sensors;

import java.util.Map;
import opencep.base.EventTypeClassifier;

public final class SensorsEventTypeClassifier implements EventTypeClassifier {
  public String getEventType(Map<String, Object> payload) {
    return payload.get("SensorType").toString();
  }
}
