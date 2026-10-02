package opencep.plugin.twitter;

import java.time.*;
import java.time.format.*;
import java.util.*;
import opencep.base.*;
import opencep.misc.Json;

public final class TweetDataFormatter extends DataFormatter {
  public static final List<String> MANDATORY_FIELDS =
      List.of(
          "id",
          "created_at",
          "text",
          "truncated",
          "in_reply_to_status_id",
          "in_reply_to_user_id",
          "in_reply_to_screen_name",
          "user",
          "is_quote_status",
          "retweet_count",
          "favorite_count",
          "favorited",
          "retweeted",
          "filter_level",
          "lang");

  public TweetDataFormatter() {
    this(new DummyTwitterEventTypeClassifier());
  }

  public TweetDataFormatter(EventTypeClassifier classifier) {
    super(classifier);
  }

  public Map<String, Object> parseEvent(String raw) {
    Object parsed = Json.parse(raw);
    if (!(parsed instanceof Map<?, ?> input))
      throw new IllegalArgumentException("Tweet must be a JSON object");
    var out = new LinkedHashMap<String, Object>();
    for (var key : MANDATORY_FIELDS) {
      if (!input.containsKey(key))
        throw new IllegalArgumentException("Missing tweet field: " + key);
      out.put(key, input.get(key));
    }
    for (var key : List.of("quoted_status_id", "quote_count", "reply_count"))
      if (input.containsKey(key)) out.put(key, input.get(key));
    for (var e : Map.of("place", "full_name", "retweeted_status", "id").entrySet())
      if (input.get(e.getKey()) instanceof Map<?, ?> nested)
        out.put(e.getKey(), nested.get(e.getValue()));
    return out;
  }

  public Instant getEventTimestamp(Map<String, Object> payload) {
    return OffsetDateTime.parse(
            payload.get("created_at").toString(),
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss xx uuuu", Locale.ENGLISH))
        .toInstant();
  }
}
