package opencep.plugin.sensors;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import opencep.base.*;
import opencep.misc.Utils;

public final class SensorsDataFormatter extends DataFormatter {
  public static final Map<String, List<String>> KEYS =
      Map.of(
          "PressTemp", List.of("SensorType", "TimeStamp", "Amplitude", "Pressure", "Temperature"),
          "Accelerometer", List.of("SensorType", "TimeStamp", "Amplitude", "AccX", "AccY", "AccZ"),
          "Magnetometer", List.of("SensorType", "TimeStamp", "Amplitude", "MagX", "MagY", "MagZ"));

  public SensorsDataFormatter() {
    this(new SensorsEventTypeClassifier());
  }

  public SensorsDataFormatter(EventTypeClassifier classifier) {
    super(classifier);
  }

  public Map<String, Object> parseEvent(String raw) {
    var cols = raw.strip().split(",", -1);
    var keys = KEYS.get(cols[0]);
    if (keys == null || cols.length != keys.size())
      throw new IllegalArgumentException("Invalid sensor row: " + raw);
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < cols.length; i++) out.put(keys.get(i), Utils.strToNumber(cols[i]));
    return out;
  }

  public Instant getEventTimestamp(Map<String, Object> payload) {
    return LocalDateTime.parse(
            payload.get("TimeStamp").toString(), DateTimeFormatter.ofPattern("MM/dd/uuuu HH:mm:ss"))
        .toInstant(ZoneOffset.UTC);
  }

  public static void generate(Path path, int count, long seed) throws IOException {
    var random = new Random(seed);
    var start = LocalDateTime.of(2020, 1, 1, 0, 0);
    var types = List.of("PressTemp", "Accelerometer", "Magnetometer");
    try (var writer = Files.newBufferedWriter(path)) {
      for (int i = 0; i < count; i++) {
        var type = types.get(random.nextInt(3));
        var line =
            new StringBuilder(type)
                .append(',')
                .append(
                    start
                        .plusSeconds(i * 30L)
                        .format(DateTimeFormatter.ofPattern("MM/dd/uuuu HH:mm:ss")));
        for (int k = 2; k < KEYS.get(type).size(); k++)
          line.append(',').append(String.format(Locale.ROOT, "%.3f", random.nextDouble() * 100));
        writer.write(line + "\n");
      }
    }
  }
}
