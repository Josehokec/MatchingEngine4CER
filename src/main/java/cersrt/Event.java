package cersrt;

import java.util.Map;
import java.util.Objects;

/** An immutable event. Timestamps use the same integer units as the input stream. */
public record Event(long id, String type, long timestamp, Map<String, Object> attributes) {
  public Event {
    Objects.requireNonNull(type, "type");
    attributes = Map.copyOf(attributes);
  }

  public Object attribute(String name) {
    return switch (name) {
      case "EventType", "eventType" -> type;
      case "timestamp" -> timestamp;
      case "id" -> id;
      default -> {
        if (!attributes.containsKey(name)) {
          throw new IllegalArgumentException("Event " + id + " has no attribute '" + name + "'");
        }
        yield attributes.get(name);
      }
    };
  }

  public double number(String name) {
    Object value = attribute(name);
    double result =
        value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
    if (!Double.isFinite(result))
      throw new IllegalArgumentException("Non-finite attribute: " + name);
    return result;
  }
}
