package cersrt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Incremental nondeterministic symbolic register transducer. One engine is one stream session. */
public final class SrtEngine {
  /**
   * maxActiveRuns is a fail-fast limit across all queries and partitions; zero disables the limit.
   */
  public record Options(boolean consume, boolean stopAfterFinal, int maxActiveRuns) {
    public Options {
      if (maxActiveRuns < 0) throw new IllegalArgumentException("Negative run limit");
    }

    public Options() {
      this(false, false, 1_000_000);
    }

    public static Options legacy() {
      return new Options(false, true, 1_000_000);
    }
  }

  public record Statistics(
      long events, long matches, long guardEvaluations, int activeRuns, int peakActiveRuns) {}

  // Immutable linked prefixes share matched history between branching runs.
  private static final class Path {
    final Event event;
    final long position;
    final Path previous;
    final int size;
    final int hash;
    final long firstPosition;
    final long firstTimestamp;

    Path(Event event, long position, Path previous) {
      this.event = event;
      this.position = position;
      this.previous = previous;
      size = previous == null ? 1 : previous.size + 1;
      hash = 31 * (previous == null ? 1 : previous.hash) + Long.hashCode(position);
      firstPosition = previous == null ? position : previous.firstPosition;
      firstTimestamp = previous == null ? event.timestamp() : previous.firstTimestamp;
    }

    List<Event> events() {
      List<Event> events = new ArrayList<>(size);
      for (Path current = this; current != null; current = current.previous)
        events.add(current.event);
      Collections.reverse(events);
      return events;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Path path) || hash != path.hash || size != path.size) return false;
      Path left = this;
      Path right = path;
      while (left != null && right != null) {
        if (left == right) return true;
        if (left.position != right.position) return false;
        left = left.previous;
        right = right.previous;
      }
      return left == right;
    }
  }

  private record Run(CompiledPattern.State state, Map<String, Event> registers, Path path) {}

  private record OutputKey(Path path, Map<String, Event> registers) {}

  private static final class Session {
    final Query query;
    final CompiledPattern compiled;
    final Map<Object, Map<Run, Run>> partitions = new LinkedHashMap<>();

    Session(Query query) {
      this.query = query;
      compiled = CompiledPattern.compile(query.expression());
    }
  }

  private final List<Session> sessions;
  private final Options options;
  private long position;
  private long matchCount;
  private long guardEvaluations;
  private long watermark;
  private boolean hasWatermark;
  private int activeRuns;
  private int peakActiveRuns;
  private boolean processing;

  public SrtEngine(Query query) {
    this(List.of(query), new Options());
  }

  public SrtEngine(List<Query> queries) {
    this(queries, new Options());
  }

  public SrtEngine(List<Query> queries, Options options) {
    if (queries.isEmpty()) throw new IllegalArgumentException("At least one query required");
    this.options = Objects.requireNonNull(options);
    sessions = queries.stream().map(Session::new).toList();
  }

  public List<CompiledPattern> compiledPatterns() {
    return sessions.stream().map(s -> s.compiled).toList();
  }

  public List<Match> accept(Event event) {
    List<Match> result = new ArrayList<>();
    accept(event, result::add);
    return List.copyOf(result);
  }

  /**
   * Streams results to the callback without retaining completed matches. Callbacks must not
   * reenter.
   */
  public void accept(Event event, Consumer<Match> output) {
    Objects.requireNonNull(event);
    Objects.requireNonNull(output);
    if (processing) throw new IllegalStateException("Engine callback cannot reenter the engine");
    if (hasWatermark && event.timestamp() < watermark)
      throw new IllegalArgumentException("Event timestamps must be nondecreasing");
    // Check all partition keys before advancing the input position.
    List<Object> keys =
        sessions.stream()
            .map(
                s ->
                    s.query.partitionBy().equals("$")
                        ? "$"
                        : event.attribute(s.query.partitionBy()))
            .toList();
    processing = true;
    try {
      watermark = event.timestamp();
      hasWatermark = true;
      position = Math.incrementExact(position);
      long before = matchCount;
      for (int queryIndex = 0; queryIndex < sessions.size(); queryIndex++)
        advance(sessions.get(queryIndex), queryIndex, keys.get(queryIndex), event, output);
      if (options.consume && matchCount > before) clearRuns();
      activeRuns =
          sessions.stream()
              .mapToInt(s -> s.partitions.values().stream().mapToInt(Map::size).sum())
              .sum();
      peakActiveRuns = Math.max(peakActiveRuns, activeRuns);
    } finally {
      processing = false;
    }
  }

  public void run(Iterable<Event> events, Consumer<Match> output) {
    for (Event event : events) accept(event, output);
  }

  public Statistics statistics() {
    return new Statistics(position, matchCount, guardEvaluations, activeRuns, peakActiveRuns);
  }

  /** Discards pending runs while preserving the stream position and statistics. */
  public void reset() {
    if (processing) throw new IllegalStateException("Cannot reset inside a callback");
    clearRuns();
    activeRuns = 0;
  }

  private void clearRuns() {
    sessions.forEach(s -> s.partitions.clear());
  }

  private boolean expired(Query query, Path path, Event event) {
    if (query.window() == 0 || path == null) return false;
    long current = query.windowType() == Query.WindowType.COUNT ? position : event.timestamp();
    long first =
        query.windowType() == Query.WindowType.COUNT ? path.firstPosition : path.firstTimestamp;
    // Both metrics are monotonic; a negative subtraction indicates signed overflow.
    long span = current - first;
    return span < 0 || span >= query.window();
  }

  private void advance(
      Session session, int queryIndex, Object partition, Event event, Consumer<Match> output) {
    for (Iterator<Map.Entry<Object, Map<Run, Run>>> iterator =
            session.partitions.entrySet().iterator();
        iterator.hasNext(); ) {
      var entry = iterator.next();
      int size = entry.getValue().size();
      entry.getValue().keySet().removeIf(run -> expired(session.query, run.path, event));
      activeRuns -= size - entry.getValue().size();
      if (entry.getValue().isEmpty()) iterator.remove();
    }
    Map<Run, Run> previous = session.partitions.getOrDefault(partition, Map.of());
    Map<Run, Run> next = new LinkedHashMap<>();
    Map<OutputKey, Boolean> emitted = new LinkedHashMap<>();
    for (Run run : previous.values())
      move(session, queryIndex, partition, event, run, next, emitted, output, previous.size());
    move(
        session,
        queryIndex,
        partition,
        event,
        new Run(session.compiled.start(), Map.of(), null),
        next,
        emitted,
        output,
        previous.size());
    activeRuns += next.size() - previous.size();
    if (next.isEmpty()) session.partitions.remove(partition);
    else session.partitions.put(partition, next);
  }

  private void move(
      Session session,
      int queryIndex,
      Object partition,
      Event event,
      Run run,
      Map<Run, Run> next,
      Map<OutputKey, Boolean> emitted,
      Consumer<Match> output,
      int oldSize) {
    for (CompiledPattern.Edge edge : session.compiled.outgoing(run.state)) {
      guardEvaluations++;
      if (!edge.guard().test(event, run.registers)) continue;
      Map<String, Event> registers = run.registers;
      if (edge.register() != null) {
        var updated = new LinkedHashMap<>(registers);
        updated.put(edge.register(), event);
        registers = Map.copyOf(updated);
      }
      Path path = edge.take() ? new Path(event, position, run.path) : run.path;
      if (path == null)
        continue; // Empty prefixes are represented by a fresh start on each input event.
      if (edge.target().accepting
          && edge.take()
          && emitted.putIfAbsent(new OutputKey(path, registers), true) == null) {
        matchCount = Math.incrementExact(matchCount);
        output.accept(
            new Match(
                queryIndex, partition, path.events(), registers, path.firstPosition, position));
      }
      if (!(options.stopAfterFinal && edge.target().accepting)
          && !session.compiled.outgoing(edge.target()).isEmpty()) {
        Run successor = new Run(edge.target(), registers, path);
        if (!next.containsKey(successor)) {
          if (options.maxActiveRuns > 0
              && (long) activeRuns - oldSize + next.size() + 1 > options.maxActiveRuns) {
            throw new IllegalStateException(
                "Active run limit exceeded ("
                    + options.maxActiveRuns
                    + "); shorten the window or increase Options.maxActiveRuns");
          }
          next.put(successor, successor);
        }
      }
    }
  }
}
