package cersrt;

import java.util.List;
import java.util.Map;

/** Selected events in input order; skipped events are excluded. */
public record Match(
    int queryIndex,
    Object partition,
    List<Event> events,
    Map<String, Event> registers,
    long startPosition,
    long endPosition) {
  public Match {
    events = List.copyOf(events);
    registers = Map.copyOf(registers);
  }

  public List<Long> eventIds() {
    return events.stream().map(Event::id).toList();
  }

  @Override
  public String toString() {
    return "MATCH query=" + queryIndex + " partition=" + partition + " events=" + eventIds();
  }
}
