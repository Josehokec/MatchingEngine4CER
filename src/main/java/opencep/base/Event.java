package opencep.base;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Event {
  public static final String INDEX_ATTRIBUTE_NAME = "InternalIndexAttributeName";
  public static final ThreadLocal<Long> CURRENT_STREAM_INDEX = ThreadLocal.withInitial(() -> 0L);
  private static final AtomicLong COUNTER = new AtomicLong();
  private static final AtomicLong AGGREGATE_COUNTER = new AtomicLong(-1);
  public final long index;
  public final String type;
  public final Instant timestamp, minTimestamp, maxTimestamp;
  public final Map<String, Object> payload;
  public final Double probability;

  public Event(String raw, DataFormatter formatter) {
    this(formatter.parseEvent(raw), formatter);
  }

  private Event(Map<String, Object> payload, DataFormatter formatter) {
    this(
        formatter.getEventType(payload),
        formatter.getEventTimestamp(payload),
        payload,
        formatter.getProbability(payload));
  }

  public Event(String type, Instant timestamp, Map<String, Object> payload) {
    this(type, timestamp, payload, null);
  }

  public Event(String type, Instant timestamp, Map<String, Object> payload, Double probability) {
    this(type, timestamp, timestamp, payload, probability);
  }

  protected Event(
      String type, Instant first, Instant last, Map<String, Object> payload, Double probability) {
    this(type, first, last, payload, probability, false);
  }

  protected Event(
      String type,
      Instant first,
      Instant last,
      Map<String, Object> payload,
      Double probability,
      boolean aggregate) {
    if (probability != null
        && (!Double.isFinite(probability) || probability < 0 || probability > 1))
      throw new IllegalArgumentException("Invalid event probability");
    this.index = aggregate ? AGGREGATE_COUNTER.getAndDecrement() : COUNTER.getAndIncrement();
    this.type = Objects.requireNonNull(type);
    this.timestamp = Objects.requireNonNull(first);
    this.minTimestamp = first;
    this.maxTimestamp = Objects.requireNonNull(last);
    this.probability = probability;
    var copy = new LinkedHashMap<>(payload);
    copy.put(INDEX_ATTRIBUTE_NAME, index);
    this.payload = Collections.unmodifiableMap(copy);
  }

  public List<Event> primitiveEvents() {
    return List.of(this);
  }

  public boolean equals(Object o) {
    return o instanceof Event e && index == e.index;
  }

  public int hashCode() {
    return Long.hashCode(index);
  }

  public String toString() {
    var visible = new LinkedHashMap<>(payload);
    visible.remove(INDEX_ATTRIBUTE_NAME);
    return visible.toString();
  }
}
