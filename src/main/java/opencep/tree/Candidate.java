package opencep.tree;

import java.time.Instant;
import java.util.*;
import opencep.base.*;
import opencep.misc.Utils;

public final class Candidate {
  private static final java.util.concurrent.atomic.AtomicLong SERIAL =
      new java.util.concurrent.atomic.AtomicLong();
  public final long serial = SERIAL.getAndIncrement();
  public final Binding binding;
  public final List<Event> events;
  public final Instant first, last;
  public final Double probability;

  public Candidate(Binding binding, List<Event> events, Double probability) {
    if (events.isEmpty()) throw new IllegalArgumentException("Empty candidate");
    this.binding = binding;
    this.events = List.copyOf(events);
    this.probability = probability;
    first = events.stream().map(e -> e.minTimestamp).min(Comparator.naturalOrder()).orElseThrow();
    last = events.stream().map(e -> e.maxTimestamp).max(Comparator.naturalOrder()).orElseThrow();
  }

  public static Candidate leaf(String name, Event event) {
    return new Candidate(Binding.of(name, event), List.of(event), event.probability);
  }

  public Candidate merge(Candidate other, boolean closure, List<String> order) {
    return merge(other, closure, order, false);
  }

  public Candidate merge(Candidate other, boolean closure, List<String> order, boolean legacy) {
    if (legacy && !closure)
      for (var a : events)
        for (var b : other.events) if (legacyIndex(a) == legacyIndex(b)) return null;
    var b = binding.merge(other.binding, closure, legacy || closure);
    if (b == null) return null;
    var es = new ArrayList<Event>();
    es.addAll(events);
    es.addAll(other.events);
    if (order != null) es.sort(Comparator.comparingInt(e -> aliasIndex(e, b, order)));
    return new Candidate(b, es, Utils.calculateJointProbability(probability, other.probability));
  }

  private static long legacyIndex(Event e) {
    return e instanceof AggregatedEvent a ? a.legacyIndex : e.index;
  }

  private int aliasIndex(Event event, Binding b, List<String> order) {
    var primitives = new HashSet<>(event.primitiveEvents());
    int best = Integer.MAX_VALUE;
    for (var name : b.names())
      if (b.events(name).stream().anyMatch(primitives::contains))
        best = Math.min(best, order.indexOf(name));
    return best;
  }

  public Candidate withProbability(Double value) {
    return new Candidate(binding, events, value);
  }

  public PatternMatch toMatch() {
    return new PatternMatch(binding, events, probability);
  }
}
