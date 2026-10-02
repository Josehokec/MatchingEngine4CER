package opencep.base;

import java.util.*;

public final class AggregatedEvent extends Event {
  public final List<Event> events;
  public final long legacyIndex = Event.CURRENT_STREAM_INDEX.get() + 1;

  public AggregatedEvent(List<Event> events) {
    this(events, false);
  }

  public AggregatedEvent(List<Event> events, boolean legacy) {
    super(
        first(events).type,
        legacy
            ? first(events).timestamp
            : events.stream().map(e -> e.minTimestamp).min(Comparator.naturalOrder()).orElseThrow(),
        legacy
            ? events.get(events.size() - 1).timestamp
            : events.stream().map(e -> e.maxTimestamp).max(Comparator.naturalOrder()).orElseThrow(),
        Map.of(),
        joint(events),
        true);
    this.events = List.copyOf(events);
  }

  private static Event first(List<Event> es) {
    if (es.isEmpty()) throw new IllegalArgumentException("Empty aggregation");
    return es.get(0);
  }

  private static Double joint(List<Event> es) {
    Double p = null;
    for (var e : es) if (e.probability != null) p = (p == null ? 1 : p) * e.probability;
    return p;
  }

  public List<Event> primitiveEvents() {
    return events.stream().flatMap(e -> e.primitiveEvents().stream()).toList();
  }

  public String toString() {
    return String.join("\n", events.stream().map(Object::toString).toList());
  }
}
