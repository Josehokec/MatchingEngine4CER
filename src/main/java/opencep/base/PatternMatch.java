package opencep.base;

import java.time.Instant;
import java.util.*;

public final class PatternMatch {
  public final List<Event> events;
  public final Binding binding;
  public final Instant firstTimestamp, lastTimestamp;
  public final Double probability;
  private final List<Integer> patternIds = new ArrayList<>();

  public PatternMatch(Binding binding, List<Event> events) {
    this(binding, events, null, false);
  }

  public PatternMatch(Binding binding, List<Event> events, Double probability) {
    this(binding, events, probability, true);
  }

  private PatternMatch(Binding binding, List<Event> events, Double explicit, boolean useExplicit) {
    this.binding = binding;
    this.events = List.copyOf(events);
    if (events.isEmpty()) throw new IllegalArgumentException("Empty match");
    firstTimestamp =
        events.stream().map(e -> e.minTimestamp).min(Comparator.naturalOrder()).orElseThrow();
    lastTimestamp =
        events.stream().map(e -> e.maxTimestamp).max(Comparator.naturalOrder()).orElseThrow();
    Double p = null;
    for (var e : primitiveEvents())
      if (e.probability != null) p = (p == null ? 1 : p) * e.probability;
    probability = useExplicit ? explicit : p;
  }

  public PatternMatch(List<Event> events) {
    this(new Binding(Map.of("events", events)), events);
  }

  public List<Event> primitiveEvents() {
    return events.stream().flatMap(e -> e.primitiveEvents().stream()).distinct().toList();
  }

  public void addPatternId(int id) {
    if (!patternIds.contains(id)) patternIds.add(id);
  }

  public List<Integer> getPatternIds() {
    return List.copyOf(patternIds);
  }

  public String toString() {
    var text = String.join("\n", events.stream().map(Object::toString).toList()) + "\n\n";
    return patternIds.isEmpty()
        ? text
        : String.join("", patternIds.stream().map(id -> id + ": " + text).toList());
  }

  public boolean equals(Object o) {
    return o instanceof PatternMatch p
        && new HashSet<>(events).equals(new HashSet<>(p.events))
        && patternIds.equals(p.patternIds);
  }

  public int hashCode() {
    return Objects.hash(new HashSet<>(events), patternIds);
  }
}
