package corecer;

import corecer.parser.plan.values.ValueType;
import corecer.runtime.events.Event;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline input adapters. All parsing and event construction finish before execution starts. */
final class CoreInputs {
  enum Format { CSV, EVENTS }
  record Source(Path path, String stream, Format format) {}
  record Loaded(List<Event> events, boolean limited) {}
  private record Input(String stream, String event, long timestamp, Object[] attributes) {}
  private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
      .withResolverStyle(java.time.format.ResolverStyle.STRICT);

  private CoreInputs() {}

  static Loaded load(List<Source> sources, long limit) throws Exception {
    List<Input> records = new ArrayList<>();
    boolean limited = false;
    for (Source source : sources) {
      if (corecer.parser.plan.Stream.getSchemaFor(source.stream()) == null)
        throw new IllegalArgumentException("Undeclared stream: " + source.stream());
      try (BufferedReader reader = Files.newBufferedReader(source.path(), StandardCharsets.UTF_8)) {
        long lineNumber = 0;
        long count = 0;
        long lastTimestamp = Long.MIN_VALUE;
        Map<String, Integer> header = null;
        char delimiter = ',';
        String line;
        while ((line = reader.readLine()) != null) {
          lineNumber++;
          if (lineNumber == 1 && line.startsWith("\ufeff")) line = line.substring(1);
          if (line.isBlank()) continue;
          try {
            if (source.format() == Format.CSV && header == null) {
              delimiter = csvDelimiter(line);
              header = header(fields(line, delimiter, false));
              continue;
            }
            if (limit >= 0 && count >= limit) {
              limited = true;
              break;
            }
            Input record = source.format() == Format.CSV
                ? csv(source.stream(), fields(line, delimiter, false), header)
                : event(source.stream(), line, count);
            if (record.timestamp() < lastTimestamp)
              throw new IllegalArgumentException("Timestamps must be nondecreasing within each input stream");
            records.add(record);
            lastTimestamp = record.timestamp();
            count++;
          } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(source.path() + ":" + lineNumber + ": " + failure.getMessage(), failure);
          }
        }
        if (source.format() == Format.CSV && header == null)
          throw new IllegalArgumentException(source.path() + ": CSV input requires a header");
      }
    }
    // List.sort is stable, so equal timestamps follow descriptor order, then file order.
    if (sources.size() > 1) records.sort(Comparator.comparingLong(Input::timestamp));
    if (limit >= 0 && records.size() > limit) {
      records = records.subList(0, (int) limit);
      limited = true;
    }
    List<Event> result = new ArrayList<>(records.size());
    for (Input record : records) {
      result.add(Event.EventWithTimestamp(record.stream(), record.event(), record.timestamp(), record.attributes()));
    }
    return new Loaded(List.copyOf(result), limited);
  }

  private static char csvDelimiter(String line) {
    return fields(line, ';', false).size() > fields(line, ',', false).size() ? ';' : ',';
  }

  private static Map<String, Integer> header(List<String> fields) {
    Map<String, Integer> header = new LinkedHashMap<>();
    for (int i = 0; i < fields.size(); i++) {
      String field = fields.get(i).trim();
      if (field.equalsIgnoreCase("event")) field = "event";
      if (field.equalsIgnoreCase("time") || field.equalsIgnoreCase("timestamp")) field = "timestamp";
      if (field.isEmpty() || header.putIfAbsent(field, i) != null)
        throw new IllegalArgumentException("CSV header has an empty or duplicate column: " + field);
    }
    if (!header.containsKey("event") || !header.containsKey("timestamp"))
      throw new IllegalArgumentException("CSV header requires event and time (or timestamp) columns");
    return header;
  }

  private static Input csv(String stream, List<String> fields, Map<String, Integer> header) {
    if (fields.size() != header.size())
      throw new IllegalArgumentException("CSV row has " + fields.size() + " columns, expected " + header.size());
    String name = fields.get(header.get("event")).trim();
    corecer.parser.plan.Event schema = schema(stream, name);
    Object[] attributes = new Object[schema.getAttributes().size()];
    for (int i = 0; i < attributes.length; i++) {
      var attribute = schema.getAttributes().get(i);
      Integer column = header.get(attribute.getKey());
      if (column == null)
        throw new IllegalArgumentException("Missing CSV column " + attribute.getKey() + " for event " + name);
      attributes[i] = value(fields.get(column), attribute.getValue());
    }
    return new Input(stream, name, timestamp(fields.get(header.get("timestamp"))), attributes);
  }

  private static Input event(String stream, String line, long fallbackTimestamp) {
    line = line.trim();
    int open = line.indexOf('(');
    if (open <= 0 || !line.endsWith(")"))
      throw new IllegalArgumentException("Expected EVENT(attribute=value,...) input");
    String name = line.substring(0, open).trim();
    corecer.parser.plan.Event schema = schema(stream, name);
    Map<String, String> supplied = new HashMap<>();
    String contents = line.substring(open + 1, line.length() - 1).trim();
    if (!contents.isEmpty()) {
      for (String field : fields(contents, ',', true)) {
        int equals = field.indexOf('=');
        if (equals <= 0) throw new IllegalArgumentException("Expected attribute=value: " + field);
        String key = field.substring(0, equals).trim();
        if (supplied.putIfAbsent(key, unquote(field.substring(equals + 1).trim())) != null)
          throw new IllegalArgumentException("Duplicate attribute " + key);
      }
    }
    long time = fallbackTimestamp;
    for (String timeName : List.of("__ts", "timestamp", "time")) {
      if (supplied.containsKey(timeName)) {
        time = timestamp(supplied.get(timeName));
        break;
      }
    }
    Object[] attributes = new Object[schema.getAttributes().size()];
    for (int i = 0; i < attributes.length; i++) {
      var attribute = schema.getAttributes().get(i);
      String text = supplied.remove(attribute.getKey());
      if (text == null) throw new IllegalArgumentException("Missing attribute " + attribute.getKey() + " for " + name);
      attributes[i] = value(text, attribute.getValue());
    }
    supplied.remove("__ts");
    supplied.remove("timestamp");
    supplied.remove("time");
    if (!supplied.isEmpty()) throw new IllegalArgumentException("Unknown attributes for " + name + ": " + supplied.keySet());
    return new Input(stream, name, time, attributes);
  }

  private static corecer.parser.plan.Event schema(String stream, String event) {
    var schema = corecer.parser.plan.Event.getSchemaFor(event);
    if (schema == null) throw new IllegalArgumentException("Undeclared event: " + event);
    if (!corecer.parser.plan.Stream.getSchemaFor(stream).containsEvent(event))
      throw new IllegalArgumentException("Event " + event + " is not declared on stream " + stream);
    return schema;
  }

  private static Object value(String text, ValueType type) {
    try {
      return switch (type) {
        case INTEGER -> Integer.valueOf(text.trim());
        case LONG -> Long.valueOf(text.trim());
        case DOUBLE, NUMERIC -> {
          double number = Double.parseDouble(text.trim());
          if (!Double.isFinite(number)) throw new IllegalArgumentException("Numeric values must be finite");
          yield number;
        }
        case BOOLEAN -> {
          if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Boolean value must be true or false: " + text);
          yield Boolean.valueOf(text);
        }
        case STRING -> text;
      };
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("Invalid " + type.name().toLowerCase(Locale.ROOT) + " value: " + text, error);
    }
  }

  static long timestamp(String text) {
    text = text.trim();
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException ignored) {
      try {
        return LocalDateTime.parse(text, UTC_TIME).toInstant(ZoneOffset.UTC).toEpochMilli();
      } catch (DateTimeParseException error) {
        throw new IllegalArgumentException("Timestamp must be milliseconds or yyyy-MM-dd HH:mm:ss UTC: " + text, error);
      }
    }
  }

  /** Single-record CSV with doubled quote escaping; events mode also retains single-quoted values. */
  private static List<String> fields(String text, char delimiter, boolean eventsMode) {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    char quote = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != 0) {
        if (eventsMode && c == '\\' && i + 1 < text.length()) {
          field.append(c).append(text.charAt(++i));
        } else if (c == quote) {
          if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
            field.append(c);
            if (eventsMode) field.append(c);
            i++;
          } else {
            quote = 0;
            if (eventsMode) field.append(c);
          }
        } else field.append(c);
      } else if (c == delimiter) {
        fields.add(field.toString());
        field.setLength(0);
      } else if ((c == '"' || (eventsMode && c == '\'')) && startsQuotedValue(field, eventsMode)) {
        quote = c;
        if (eventsMode) field.append(c);
      } else field.append(c);
    }
    if (quote != 0) throw new IllegalArgumentException("Unterminated quoted value; multiline records are unsupported");
    fields.add(field.toString());
    return fields;
  }

  private static boolean startsQuotedValue(StringBuilder field, boolean eventsMode) {
    int start = eventsMode ? field.indexOf("=") + 1 : 0;
    // Native data has unquoted names such as Prince's Bay: only an opening value
    // delimiter starts quoting, while punctuation inside an unquoted value is literal.
    return (!eventsMode || start > 0) && field.substring(start).isBlank();
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
      char quote = value.charAt(0);
      if (value.charAt(value.length() - 1) != quote) throw new IllegalArgumentException("Invalid quoted value: " + value);
      return value.substring(1, value.length() - 1)
          .replace("" + quote + quote, "" + quote).replace("\\" + quote, "" + quote);
    }
    return value;
  }
}
