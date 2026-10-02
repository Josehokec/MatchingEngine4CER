package opencep.base;

import java.util.Map;

@FunctionalInterface
public interface EventTypeClassifier {
  String getEventType(Map<String, Object> payload);
}
