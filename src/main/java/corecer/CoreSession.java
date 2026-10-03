package corecer;

import corecer.execution.BaseExecutor;
import corecer.execution.structures.output.CDSComplexEventGrouping;
import corecer.execution.structures.output.ComplexEvent;
import corecer.parser.CORELexer;
import corecer.parser.COREParser;
import corecer.parser.CompoundStatementParser;
import corecer.parser.DeclarationParser;
import corecer.parser.QueryParser;
import corecer.parser.plan.Label;
import corecer.parser.plan.Stream;
import corecer.runtime.events.Event;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * Synchronous adapter over the upstream CORE executor. Only one session may be open in a JVM:
 * upstream schemas, labels, and predicate factories are static. Close the session before opening
 * another one. Events from a closed session must not be reused in the next session.
 */
public final class CoreSession implements AutoCloseable {
  private static boolean activeSession;
  private final List<BaseExecutor> executors = new ArrayList<>();
  private final Options options;
  private final Consumer<Match> listener;
  private final long compileNanos;
  private boolean closed;
  private boolean sending;
  private long deadlineNanos;
  private long events;
  private long enumeratedMatches;
  private long triggers;
  private long truncatedTriggers;
  private long processNanos;
  private long enumerationNanos;
  private boolean timedOut;

  /** An enumeration limit of zero enumerates every match of each query trigger. */
  public record Options(boolean enumerate, long enumerationLimit) {
    public Options {
      if (enumerationLimit < 0) throw new IllegalArgumentException("Enumeration limit must be nonnegative");
    }

    public static Options defaults() {
      return new Options(true, 0);
    }
  }

  /** One detached match. Event indices are zero based within this session, in stream order. */
  public record Match(int queryNumber, long triggerIndex, List<Long> eventIndices) {
    public Match {
      eventIndices = List.copyOf(eventIndices);
    }
  }

  /** Matching time excludes callback enumeration; enumeration time includes the listener. */
  public record Metrics(
      long events,
      long enumeratedMatches,
      long triggers,
      long truncatedTriggers,
      long processNanos,
      long enumerationNanos,
      boolean timedOut,
      boolean enumerationEnabled) {
    public boolean truncated() {
      return truncatedTriggers != 0;
    }
  }

  public CoreSession(String declarations, String queries, Consumer<Match> listener) {
    this(declarations, List.of(queries), Options.defaults(), listener);
  }

  public CoreSession(String declarations, List<String> queries, Consumer<Match> listener) {
    this(declarations, queries, Options.defaults(), listener);
  }

  public CoreSession(
      String declarations, String queries, Options options, Consumer<Match> listener) {
    this(declarations, List.of(queries), options, listener);
  }

  public CoreSession(
      String declarations, List<String> querySources, Options options, Consumer<Match> listener) {
    this.options = Objects.requireNonNull(options, "options");
    this.listener = listener;
    Objects.requireNonNull(declarations, "declarations");
    Objects.requireNonNull(querySources, "queries");
    long start = System.nanoTime();
    synchronized (CoreSession.class) {
      if (activeSession)
        throw new IllegalStateException("Only one CORE session may be active; close it before creating another");
      activeSession = true;
      try {
        Label.invalidateLabelsSchema();
        corecer.parser.plan.Event.invalidateEventSchema();
        Stream.invalidateStreamsSchema();
        Event.resetIndex();
        corecer.runtime.profiling.Profiler.reset();
        var declarationsParser = new CompoundStatementParser(new DeclarationParser(), new QueryParser());
        if (!declarationsParser.parse(declarations).isEmpty())
          throw new IllegalArgumentException("Declarations source must contain only event and stream declarations");
        for (String source : querySources) {
          for (String query : splitQueries(Objects.requireNonNull(source, "query source"))) {
            // PredicateFactory is global: materialize the executor before compiling the next query.
            BaseExecutor executor = BaseExecutor.fromPlan(new QueryParser().parse(query));
            int number = executors.size() + 1;
            executor.setQuery(query);
            executor.setMatchCallback(grouping -> enumerate(number, grouping));
            executors.add(executor);
          }
        }
        if (executors.isEmpty()) throw new IllegalArgumentException("At least one CORE query is required");
      } catch (RuntimeException | Error failure) {
        activeSession = false;
        closed = true;
        throw failure;
      }
    }
    compileNanos = System.nanoTime() - start;
  }

  /** Accepts adjacent SELECT statements or the original files' semicolon/newline separators. */
  static List<String> splitQueries(String source) {
    CORELexer lexer = new CORELexer(CharStreams.fromString(source));
    lexer.removeErrorListeners();
    lexer.addErrorListener(STRICT_ERRORS);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();
    List<Token> visible = tokens.getTokens().stream()
        .filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL && t.getType() != Token.EOF).toList();
    if (visible.isEmpty()) throw new IllegalArgumentException("Query source is empty");
    if (visible.get(0).getType() != CORELexer.K_SELECT)
      throw new IllegalArgumentException("Query source must start with SELECT");
    List<String> result = new ArrayList<>();
    for (int begin = 0; begin < visible.size();) {
      int next = begin + 1;
      while (next < visible.size() && visible.get(next).getType() != CORELexer.K_SELECT) next++;
      int end = next - 1;
      if (visible.get(end).getType() == CORELexer.SEMICOLON) end--;
      if (end < begin) throw new IllegalArgumentException("Empty CORE query");
      String query = source.substring(visible.get(begin).getStartIndex(), visible.get(end).getStopIndex() + 1);
      CORELexer validationLexer = new CORELexer(CharStreams.fromString(query));
      validationLexer.removeErrorListeners();
      validationLexer.addErrorListener(STRICT_ERRORS);
      COREParser parser = new COREParser(new CommonTokenStream(validationLexer));
      parser.removeErrorListeners();
      parser.addErrorListener(STRICT_ERRORS);
      COREParser.ParseContext tree = parser.parse();
      if (tree.core_stmt().size() != 1 || tree.core_stmt(0).core_query() == null)
        throw new IllegalArgumentException("Expected exactly one SELECT statement per query");
      result.add(query);
      begin = next;
    }
    return List.copyOf(result);
  }

  private static final BaseErrorListener STRICT_ERRORS = new BaseErrorListener() {
    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer, Object symbol, int line, int column,
        String message, RecognitionException error) {
      throw new IllegalArgumentException("CORE syntax error at " + line + ":" + (column + 1) + ": " + message);
    }
  };

  /** Starts a processing deadline now; zero disables the timeout. Loading and compilation are excluded. */
  public synchronized void setTimeoutSeconds(double seconds) {
    ensureOpen();
    if (!Double.isFinite(seconds) || seconds < 0)
      throw new IllegalArgumentException("Timeout must be a finite nonnegative number");
    if (seconds > Long.MAX_VALUE / 1e9)
      throw new IllegalArgumentException("Timeout is too large");
    deadlineNanos = seconds == 0 ? 0 : System.nanoTime() + (long) (seconds * 1e9);
    timedOut = false;
  }

  public synchronized boolean timedOut() {
    if (deadlineNanos != 0 && System.nanoTime() - deadlineNanos >= 0) timedOut = true;
    return timedOut;
  }

  /** The callback runs on this thread; it must not send another event or close this session. */
  public synchronized void sendEvent(Event event) {
    ensureOpen();
    Objects.requireNonNull(event, "event");
    if (sending) throw new IllegalStateException("CORE callbacks must not recursively send events");
    sending = true;
    long start = System.nanoTime();
    long previousEnumeration = enumerationNanos;
    try {
      for (BaseExecutor executor : executors) executor.sendEvent(event);
      events++;
    } finally {
      processNanos += Math.max(0, System.nanoTime() - start - (enumerationNanos - previousEnumeration));
      sending = false;
    }
  }

  public synchronized void sendEvent(
      String stream, String eventName, long timestamp, Object... attributes)
      throws corecer.exceptions.EventException {
    ensureOpen();
    sendEvent(Event.EventWithTimestamp(stream, eventName, timestamp, attributes));
  }

  private void enumerate(int queryNumber, CDSComplexEventGrouping grouping) {
    triggers++;
    if (!options.enumerate()) {
      truncatedTriggers++;
      return;
    }
    long start = System.nanoTime();
    try {
      if (timedOut()) {
        truncatedTriggers++;
        return;
      }
      Iterator<ComplexEvent> matches = grouping.iterator();
      long count = 0;
      while (matches.hasNext()) {
        if ((options.enumerationLimit() != 0 && count >= options.enumerationLimit()) || timedOut()) {
          truncatedTriggers++;
          break;
        }
        ComplexEvent match = matches.next();
        if (match == null) continue;
        List<Long> indices = new ArrayList<>();
        for (Event event : match) {
          if (event.getType() >= 0 && event.getName() != null) indices.add(event.getIndex());
        }
        Match detached = new Match(queryNumber, grouping.getLastEvent().getIndex(), indices);
        count++;
        enumeratedMatches++;
        if (listener != null) listener.accept(detached);
      }
    } finally {
      enumerationNanos += System.nanoTime() - start;
    }
  }

  public long compileNanos() {
    return compileNanos;
  }

  public synchronized int queryCount() {
    return executors.size();
  }

  public synchronized Metrics metrics() {
    return new Metrics(events, enumeratedMatches, triggers, truncatedTriggers, processNanos,
        enumerationNanos, timedOut, options.enumerate());
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("CORE session is closed");
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    if (sending) throw new IllegalStateException("CORE callbacks must not close the session");
    executors.clear();
    closed = true;
    synchronized (CoreSession.class) {
      activeSession = false;
    }
  }
}
