package corecer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;

/** Standalone semantic regression tests; no JUnit or upstream service/stream threads required. */
public final class CoreRegressionSuite {
  private static int checks;
  private static String declarations;

  private CoreRegressionSuite() {}

  public static void main(String[] args) throws Exception {
    Path fixtures = Path.of("test-data/core-cer/parity/fixtures.json");
    declarations = new JSONObject(Files.readString(fixtures)).getString("declarations");
    CoreParitySuite.main(new String[] {fixtures.toString()});
    boundedDifferential();
    multiQueryCompilation();
    partitionAndConsumption();
    enumerationAndLifecycle();
    invalidInput();
    CoreInputSuite.main(new String[0]);
    System.out.println("CORE regression suite: " + checks + " additional checks passed");
  }

  private static void boundedDifferential() throws Exception {
    Random random = new Random(816690);
    List<String> streams = new ArrayList<>(List.of("AABBC", "ABBBC", "CABBACBBC", "XAXBBCX"));
    for (int trial = 0; trial < 80; trial++) {
      StringBuilder stream = new StringBuilder();
      int length = 1 + random.nextInt(9);
      for (int i = 0; i < length; i++) stream.append("ABCX".charAt(random.nextInt(4)));
      streams.add(stream.toString());
    }
    for (String stream : streams) {
      for (String strategy : List.of("ALL", "NEXT", "LAST", "MAX")) {
        for (boolean kleene : List.of(false, true)) {
          String query = "SELECT " + strategy + " * FROM S WHERE A; B" + (kleene ? "+" : "")
              + "; C WITHIN 100 EVENTS CONSUME BY NONE";
          JSONObject input = new JSONObject().put("query", query).put("types", stream);
          equal(CoreParitySuite.run(input), enumerateABC(stream, strategy, kleene),
              strategy + " " + (kleene ? "kleene" : "sequence") + " on " + stream);
        }
      }
    }
  }

  /** Independent subsequence enumeration, with selection applied separately per start/end pair. */
  private static List<List<Long>> enumerateABC(String stream, String strategy, boolean kleene) {
    List<List<Long>> out = new ArrayList<>();
    for (int start = 0; start < stream.length(); start++) {
      if (stream.charAt(start) != 'A') continue;
      for (int end = start + 2; end < stream.length(); end++) {
        if (stream.charAt(end) != 'C') continue;
        List<Long> middle = new ArrayList<>();
        for (int i = start + 1; i < end; i++) if (stream.charAt(i) == 'B') middle.add((long) i);
        if (middle.isEmpty()) continue;
        List<List<Long>> selected = new ArrayList<>();
        if (!kleene) {
          if (strategy.equals("NEXT")) selected.add(List.of(middle.get(0)));
          else if (strategy.equals("LAST")) selected.add(List.of(middle.get(middle.size() - 1)));
          else for (long index : middle) selected.add(List.of(index));
        } else if (strategy.equals("ALL")) {
          for (int mask = 1; mask < (1 << middle.size()); mask++) {
            List<Long> subset = new ArrayList<>();
            for (int i = 0; i < middle.size(); i++) if ((mask & (1 << i)) != 0) subset.add(middle.get(i));
            selected.add(subset);
          }
        } else {
          selected.add(middle);
        }
        for (List<Long> selectedMiddle : selected) {
          List<Long> match = new ArrayList<>();
          match.add((long) start);
          match.addAll(selectedMiddle);
          match.add((long) end);
          out.add(List.copyOf(match));
        }
      }
    }
    return CoreParitySuite.sorted(out);
  }

  private static void multiQueryCompilation() throws Exception {
    List<CoreSession.Match> matches = new ArrayList<>();
    List<String> queries = List.of(
        "SELECT ALL * FROM S WHERE (A FILTER A[v >= 2]); B WITHIN 100 EVENTS CONSUME BY NONE",
        "SELECT ALL * FROM S WHERE (B FILTER B[key = 'y']); (C FILTER C[v < 10]) WITHIN 100 EVENTS CONSUME BY NONE",
        "SELECT ALL * FROM S WHERE A; C WITHIN 100 EVENTS CONSUME BY NONE");
    try (CoreSession session = new CoreSession(declarations, queries, matches::add)) {
      send(session, "A", "x", 1);
      send(session, "A", "x", 3);
      send(session, "B", "y", 4);
      send(session, "C", "y", 5);
      equal(session.queryCount(), 3, "independently compiled queries");
      equal(matches.stream().map(m -> m.queryNumber() + ":" + m.eventIndices()).sorted().toList(),
          List.of("1:[1, 2]", "2:[2, 3]", "3:[0, 3]", "3:[1, 3]"),
          "predicate factories remain attached to their own query");
      equal(session.metrics().events(), 4L, "processed event count");
      equal(session.metrics().enumeratedMatches(), 4L, "enumerated match count");
      equal(session.metrics().triggers(), 3L, "trigger count is per query and event");
      fails(UnsupportedOperationException.class, () -> matches.get(0).eventIndices().add(99L),
          "callback matches are immutable snapshots");
    }
    matches.clear();
    String adjacent = "SELECT ALL * FROM S WHERE A; B WITHIN 100 EVENTS CONSUME BY NONE;\n"
        + "SELECT ALL * FROM S WHERE B; C WITHIN 100 EVENTS CONSUME BY NONE;";
    try (CoreSession session = new CoreSession(declarations, adjacent, matches::add)) {
      send(session, "A", "x", 1);
      send(session, "B", "x", 2);
      send(session, "C", "x", 3);
      equal(session.queryCount(), 2, "original adjacent SELECT format");
      equal(matches.stream().map(CoreSession.Match::eventIndices).toList(),
          List.of(List.of(0L, 1L), List.of(1L, 2L)), "sequence semicolon is not a query separator");
    }
  }

  private static void partitionAndConsumption() throws Exception {
    for (String consume : List.of("NONE", "PARTITION", "ANY")) {
      List<CoreSession.Match> matches = new ArrayList<>();
      String query = "SELECT ALL * FROM S WHERE A; B PARTITION BY [key] WITHIN 100 EVENTS CONSUME BY " + consume;
      try (CoreSession session = new CoreSession(declarations, query, matches::add)) {
        send(session, "A", "x", 1);
        send(session, "A", "y", 2);
        send(session, "B", "x", 3);
        send(session, "B", "y", 4);
        List<List<Long>> expected = consume.equals("ANY")
            ? List.of(List.of(0L, 2L)) : List.of(List.of(0L, 2L), List.of(1L, 3L));
        equal(matches.stream().map(CoreSession.Match::eventIndices).toList(), expected,
            "partition callbacks and " + consume + " consumption");
      }
    }
  }

  private static void enumerationAndLifecycle() throws Exception {
    List<CoreSession.Match> matches = new ArrayList<>();
    String query = "SELECT ALL * FROM S WHERE A; B WITHIN 100 EVENTS CONSUME BY NONE";
    CoreSession closed;
    try (CoreSession session = new CoreSession(declarations, query, new CoreSession.Options(true, 1), matches::add)) {
      closed = session;
      send(session, "A", "x", 1);
      send(session, "A", "x", 2);
      send(session, "B", "x", 3);
      equal(matches.size(), 1, "bounded callback enumeration");
      equal(session.metrics().truncatedTriggers(), 1L, "bounded enumeration reports truncation");
      fails(IllegalStateException.class, () -> new CoreSession(declarations, query, null),
          "concurrent sessions cannot overwrite global schemas");
    }
    fails(IllegalStateException.class, () -> send(closed, "A", "x", 1), "closed session rejects events");
    matches.clear();
    try (CoreSession session = new CoreSession(declarations, query, new CoreSession.Options(false, 0), matches::add)) {
      send(session, "A", "x", 1);
      send(session, "B", "x", 2);
      equal(matches.size(), 0, "match detection without enumeration");
      equal(session.metrics().triggers(), 1L, "detection still counts triggers");
      equal(session.metrics().enumerationEnabled(), false, "metrics distinguish detection mode");
    }
    try (CoreSession session = new CoreSession(declarations, query, matches::add)) {
      send(session, "A", "x", 1);
      send(session, "B", "x", 2);
      equal(matches.get(0).eventIndices(), List.of(0L, 1L), "new session resets event positions");
    }
  }

  private static void invalidInput() throws Exception {
    for (String bad : List.of(
        "SELECT ALL * FROM S WHERE Missing WITHIN 10 EVENTS",
        "SELECT ALL * FROM S WHERE A; WITHIN 10 EVENTS",
        "SELECT ANY * FROM S WHERE A WITHIN 10 EVENTS",
        "SELECT STRICT * FROM S WHERE A WITHIN 10 EVENTS",
        "SELECT ALL * FROM S WHERE A WITHIN 10 EVENTS trailing",
        "SELECT ALL * FROM S WHERE A PARTITION BY [absent] WITHIN 10 EVENTS")) {
      fails(RuntimeException.class, () -> new CoreSession(declarations, bad, null), "reject invalid query " + bad);
    }
    fails(RuntimeException.class,
        () -> new CoreSession("DECLARE EVENT A(id int) DECLARE EVENT A(id int)",
            "SELECT * WHERE A", null), "duplicate declarations");
    // Preserved upstream limitations: math-expression attributes are lost by getAttributes(),
    // and the first pass treats a SELECT alias as an event schema name before AS is visited.
    fails(RuntimeException.class,
        () -> new CoreSession(declarations,
            "SELECT ALL * FROM S WHERE A FILTER A[v + 1 >= 3] WITHIN 10 EVENTS", null),
        "upstream composite arithmetic filter limitation");
    fails(RuntimeException.class,
        () -> new CoreSession(declarations,
            "SELECT alias FROM S WHERE A AS alias WITHIN 10 EVENTS", null),
        "upstream alias projection limitation");
    try (CoreSession session = new CoreSession(declarations,
        "SELECT ALL * FROM S WHERE A WITHIN 10 EVENTS", null)) {
      fails(corecer.exceptions.EventException.class,
          () -> session.sendEvent("Unknown", "A", 0, 1, "x", 1.0, 1L), "unknown stream");
      fails(corecer.exceptions.EventException.class,
          () -> session.sendEvent("S", "Unknown", 0, 1, "x", 1.0, 1L), "unknown event");
      fails(corecer.exceptions.EventException.class,
          () -> session.sendEvent("S", "A", 0, 1), "wrong field count");
      fails(corecer.exceptions.EventException.class,
          () -> session.sendEvent("S", "A", 0, 1, 42, 1.0, 1L), "wrong field datatype");
    }
  }

  private static void send(CoreSession session, String type, String key, double value) throws Exception {
    long index = session.metrics().events();
    session.sendEvent("S", type, index * 100, (int) index, key, value, index);
  }

  private static void equal(Object actual, Object expected, String description) {
    CoreParitySuite.equal(actual, expected, description);
    checks++;
  }

  @FunctionalInterface
  private interface ThrowingAction { void run() throws Exception; }

  private static void fails(Class<? extends Throwable> expected, ThrowingAction action, String description)
      throws Exception {
    try {
      action.run();
    } catch (Throwable failure) {
      if (!expected.isInstance(failure)) throw new AssertionError(description + ": unexpected exception", failure);
      checks++;
      return;
    }
    throw new AssertionError(description + ": accepted invalid input");
  }
}
