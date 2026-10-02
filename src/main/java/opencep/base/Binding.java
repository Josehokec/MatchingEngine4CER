package opencep.base;

import java.util.*;

/** A variable binds one event, or a list for a closure. */
public final class Binding {
  private final Map<String, List<Event>> events;
  private final Set<String> closureNames;
  private final Map<Set<String>, List<Event>> closureSequences;

  public Binding(Map<String, List<Event>> values) {
    this(values, Set.of(), Map.of());
  }

  private Binding(
      Map<String, List<Event>> values,
      Set<String> closed,
      Map<Set<String>, List<Event>> sequences) {
    closureSequences = Map.copyOf(sequences);
    closureNames = Set.copyOf(closed);
    var copy = new LinkedHashMap<String, List<Event>>();
    values.forEach((k, v) -> copy.put(k, List.copyOf(v)));
    events = Collections.unmodifiableMap(copy);
  }

  public static Binding of(String name, Event e) {
    return new Binding(Map.of(name, List.of(e)));
  }

  public Set<String> closureNames() {
    return closureNames;
  }

  public Binding markClosure(Set<String> names, List<Event> sequence) {
    var all = new HashSet<>(closureNames);
    all.addAll(names);
    var sequences = new HashMap<>(closureSequences);
    sequences.put(Set.copyOf(names), List.copyOf(sequence));
    return new Binding(events, all, sequences);
  }

  public List<Event> closureEvents(Set<String> names) {
    return closureSequences.get(names);
  }

  public Set<String> names() {
    return events.keySet();
  }

  public List<Event> events(String name) {
    var es = events.get(name);
    if (es == null) throw new IllegalArgumentException("Unbound variable: " + name);
    return es;
  }

  public Map<String, List<Event>> asMap() {
    return events;
  }

  public List<Event> allEvents() {
    return events.values().stream().flatMap(Collection::stream).distinct().toList();
  }

  public Binding merge(Binding other, boolean closure) {
    return merge(other, closure, closure);
  }

  public Binding merge(Binding other, boolean closure, boolean allowReuse) {
    var out = new LinkedHashMap<>(events);
    for (var entry : other.events.entrySet()) {
      if (out.containsKey(entry.getKey())) {
        if (!closure) return null;
        var list = new ArrayList<>(out.get(entry.getKey()));
        list.addAll(entry.getValue());
        out.put(entry.getKey(), list);
      } else out.put(entry.getKey(), entry.getValue());
    }
    var left = new HashSet<>(allEvents());
    if (!allowReuse && other.allEvents().stream().anyMatch(left::contains)) return null;
    var closed = new HashSet<>(closureNames);
    closed.addAll(other.closureNames);
    var sequences = new HashMap<>(closureSequences);
    sequences.putAll(other.closureSequences);
    return new Binding(out, closed, sequences);
  }

  public String key() {
    return events.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            e -> e.getKey() + ":" + e.getValue().stream().map(v -> Long.toString(v.index)).toList())
        .reduce("", (a, b) -> a + "|" + b);
  }
}
