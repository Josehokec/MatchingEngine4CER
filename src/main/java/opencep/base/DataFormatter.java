package opencep.base;

import java.time.Instant;
import java.util.Map;

public abstract class DataFormatter {
  private final EventTypeClassifier classifier;

  protected DataFormatter(EventTypeClassifier classifier) {
    this.classifier = classifier;
  }

  public abstract Map<String, Object> parseEvent(String rawData);

  public abstract Instant getEventTimestamp(Map<String, Object> payload);

  public String getEventType(Map<String, Object> payload) {
    return classifier.getEventType(payload);
  }

  public Double getProbability(Map<String, Object> payload) {
    return null;
  }
}
