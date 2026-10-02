package opencep.plugin.stocks;

import java.time.*;
import java.time.format.*;
import java.util.*;
import opencep.base.*;
import opencep.misc.Utils;

public final class MetastockDataFormatter extends DataFormatter {
  public static final List<String> COLUMN_KEYS =
      List.of(
          "Stock Ticker",
          "Date",
          "Opening Price",
          "Peak Price",
          "Lowest Price",
          "Close Price",
          "Volume",
          "Probability");

  public MetastockDataFormatter() {
    this(new MetastockByTickerEventTypeClassifier());
  }

  public MetastockDataFormatter(EventTypeClassifier classifier) {
    super(classifier);
  }

  public Map<String, Object> parseEvent(String raw) {
    var cols = raw.strip().split(",", -1);
    if (cols.length < 7 || cols.length > 8)
      throw new IllegalArgumentException("Expected 7 or 8 Metastock columns: " + raw);
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < cols.length; i++) out.put(COLUMN_KEYS.get(i), Utils.strToNumber(cols[i]));
    return out;
  }

  public Instant getEventTimestamp(Map<String, Object> payload) {
    return LocalDateTime.parse(
            payload.get("Date").toString(),
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT))
        .toInstant(ZoneOffset.UTC);
  }

  public Double getProbability(Map<String, Object> payload) {
    Object p = payload.get("Probability");
    if (p == null) return null;
    if (!(p instanceof Number n)) throw new IllegalArgumentException("Invalid probability");
    return n.doubleValue();
  }
}
