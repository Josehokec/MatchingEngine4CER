package cersrt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import opencep.misc.Json;

/** The three native experiment record formats, plus a generic JSON-lines format. */
public final class EventFormats {
  private EventFormats() {}

  public enum Domain {
    STOCK,
    HOME,
    TAXI,
    JSON
  }

  public static Domain domain(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "stock", "stocks" -> Domain.STOCK;
      case "home", "homes", "smart", "smarthome" -> Domain.HOME;
      case "taxi", "taxis" -> Domain.TAXI;
      case "json" -> Domain.JSON;
      default -> throw new IllegalArgumentException("Unknown domain: " + name);
    };
  }

  public static Event parse(String line, Domain domain) {
    if (domain == Domain.JSON) return json(line);
    String text = line.trim();
    int left = text.indexOf('(');
    if (left < 1 || !text.endsWith(")"))
      throw new IllegalArgumentException("Expected TYPE(key=value,...) record");
    String type = text.substring(0, left).trim();
    Map<String, String> fields = new LinkedHashMap<>();
    for (String field : split(text.substring(left + 1, text.length() - 1))) {
      int equals = field.indexOf('=');
      if (equals < 1) throw new IllegalArgumentException("Expected key=value field: " + field);
      String key = field.substring(0, equals).trim();
      if (fields.putIfAbsent(key, unquote(field.substring(equals + 1).trim())) != null)
        throw new IllegalArgumentException("Repeated field: " + key);
    }
    long id = Long.parseLong(required(fields, "id"));
    Map<String, Object> attributes = new LinkedHashMap<>();
    long timestamp;
    switch (domain) {
      case STOCK -> {
        if (!type.equals("BUY") && !type.equals("SELL"))
          throw new IllegalArgumentException("Stock event must be BUY or SELL");
        timestamp = Long.parseLong(required(fields, "timestamp"));
        attributes.put("name", required(fields, "name"));
        attributes.put("volume", Long.parseLong(required(fields, "volume")));
        attributes.put("price", number(required(fields, "price")));
      }
      case HOME -> {
        if (!type.equals("LOAD")) throw new IllegalArgumentException("Home event must be LOAD");
        String time = requiredAny(fields, "plug_timestamp", "timestamp");
        timestamp = Long.parseLong(time);
        attributes.put("plug_timestamp", time);
        attributes.put("value", number(required(fields, "value")));
        attributes.put(
            "householdId", Long.parseLong(requiredAny(fields, "household_id", "householdId")));
      }
      case TAXI -> {
        if (!type.equals("TRIP")) throw new IllegalArgumentException("Taxi event must be TRIP");
        timestamp = Long.parseLong(requiredAny(fields, "dropoff_datetime", "timestamp"));
        attributes.put(
            "pickupZone",
            requiredAny(fields, "pickup_zone", "pickupZone").replaceAll("[\\s/]", ""));
        attributes.put(
            "dropoffZone",
            requiredAny(fields, "dropoff_zone", "dropoffZone").replaceAll("[\\s/]", ""));
        attributes.put("totalAmount", number(requiredAny(fields, "total_amount", "totalAmount")));
      }
      default -> throw new IllegalArgumentException("Unsupported domain");
    }
    return new Event(id, type, timestamp, attributes);
  }

  private static Event json(String line) {
    Object decoded = Json.parse(line);
    if (!(decoded instanceof Map<?, ?> map))
      throw new IllegalArgumentException("Event JSON must be an object");
    Map<String, Object> attributes = new LinkedHashMap<>();
    Object payload = map.get("attributes");
    if (!(payload instanceof Map<?, ?> values))
      throw new IllegalArgumentException("Event JSON requires an attributes object");
    for (var entry : values.entrySet()) {
      if (!(entry.getKey() instanceof String key))
        throw new IllegalArgumentException("Invalid attribute key");
      attributes.put(key, entry.getValue());
    }
    Object type = map.get("type");
    if (!(type instanceof String name))
      throw new IllegalArgumentException("Event JSON requires a type string");
    return new Event(integer(map.get("id")), name, integer(map.get("timestamp")), attributes);
  }

  private static long integer(Object value) {
    if (!(value instanceof Number))
      throw new IllegalArgumentException("Event id and timestamp must be integers");
    try {
      return new java.math.BigDecimal(value.toString()).longValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Event id or timestamp is outside the integer range", exception);
    }
  }

  private static double number(String text) {
    double value = Double.parseDouble(text);
    if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite event value");
    return value;
  }

  private static String required(Map<String, String> fields, String name) {
    String value = fields.get(name);
    if (value == null || value.isEmpty())
      throw new IllegalArgumentException("Missing field: " + name);
    return value;
  }

  private static String requiredAny(
      Map<String, String> fields, String nativeName, String normalized) {
    return required(fields, fields.containsKey(nativeName) ? nativeName : normalized);
  }

  private static List<String> split(String body) {
    List<String> fields = new ArrayList<>();
    boolean quoted = false;
    int start = 0;
    for (int i = 0; i < body.length(); i++) {
      if (body.charAt(i) == '"') {
        if (quoted && i + 1 < body.length() && body.charAt(i + 1) == '"') i++;
        else quoted = !quoted;
      } else if (body.charAt(i) == ',' && !quoted) {
        fields.add(body.substring(start, i));
        start = i + 1;
      }
    }
    if (quoted) throw new IllegalArgumentException("Unterminated quoted field");
    fields.add(body.substring(start));
    return fields;
  }

  private static String unquote(String value) {
    return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
        ? value.substring(1, value.length() - 1).replace("\"\"", "\"")
        : value;
  }
}
