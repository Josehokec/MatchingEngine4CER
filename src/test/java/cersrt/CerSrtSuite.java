package cersrt;

import static cersrt.Expression.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Executable semantic tests and an independent bounded expression interpreter. */
public final class CerSrtSuite {
  private static int checks;

  private CerSrtSuite() {}

  public static void main(String[] args) throws Exception {
    basicSemantics();
    parserAndFormats();
    streaming();
    differential();
    System.out.println("CER-SRT semantic suite: " + checks + " checks passed");
  }

  private static void basicSemantics() {
    List<Event> events = types("AXBB C".replace(" ", ""));
    equal(
        ids(new Query(any(seq(type("A"), type("B"), type("C"))), 100), events),
        Set.of("[1, 3, 5]", "[1, 4, 5]"),
        "any selection");
    equal(
        ids(new Query(seq(type("A"), type("B"), type("C")), 100), events),
        Set.of(),
        "strict selection");
    equal(
        ids(new Query(next(seq(type("A"), type("B"), type("C"))), 100), events),
        Set.of("[1, 3, 5]"),
        "next selection");
    equal(
        ids(new Query(any(seq(type("A"), type("B"))), 3), types("AXXB")),
        Set.of(),
        "exclusive window boundary");
    equal(
        ids(new Query(any(seq(type("A"), type("B"))), 4), types("AXXB")),
        Set.of("[1, 4]"),
        "inside window");
    equal(
        ids(new Query(any(seq(type("A"), type("B"))), 0), types("AXXXB")),
        Set.of("[1, 5]"),
        "unbounded window");
    equal(
        ids(new Query(any(seq(type("A"), star(type("B")), type("C"))), 100), types("AC")),
        Set.of("[1, 2]"),
        "zero repetitions");
    equal(
        ids(new Query(any(star(type("B"))), 100), types("BXB")),
        Set.of("[1]", "[3]", "[1, 3]"),
        "standalone repetition continues");
    SrtEngine legacy =
        new SrtEngine(List.of(new Query(any(star(type("B"))), 100)), SrtEngine.Options.legacy());
    List<Match> completed = new ArrayList<>();
    legacy.run(types("BB"), completed::add);
    equal(completed.size(), 2, "legacy final states stop");
    equal(
        ids(new Query(choice(type("A"), type("A")), 100), types("A")),
        Set.of("[1]"),
        "equivalent branches emit one group");
    Query memory =
        new SreParser()
            .parse(
                "#(;(IsEventTypePredicate(A)[\"x\"],^(IsEventTypePredicate(B),GTAttr(v,\"x\")))){window:10}{windowType:time}")
            .get(0);
    List<Event> priced =
        List.of(
            event(1, "A", 1, 10, "x"),
            event(2, "A", 2, 20, "x"),
            event(3, "B", 3, 15, "x"),
            event(4, "B", 4, 21, "x"));
    equal(ids(memory, priced), Set.of("[1, 3]", "[1, 4]", "[2, 4]"), "branch registers isolated");
    Query rewrite =
        new Query(
            any(
                seq(
                    atom((e, r) -> e.type().equals("A"), "x"),
                    any(star(atom((e, r) -> e.type().equals("B"), "x"))),
                    atom(
                        (e, r) -> e.type().equals("C") && e.number("v") > r.get("x").number("v")))),
            100);
    equal(
        ids(
            rewrite,
            List.of(
                event(1, "A", 1, 10, "x"), event(2, "B", 2, 20, "x"), event(3, "C", 3, 15, "x"))),
        Set.of("[1, 3]"),
        "last register write used");
    Query partition =
        new Query(any(seq(type("A"), type("B"))), 100, Query.WindowType.TIME, "key", 0);
    equal(
        ids(
            partition,
            List.of(event(1, "A", 1, 0, "x"), event(2, "B", 2, 0, "y"), event(3, "B", 3, 0, "x"))),
        Set.of("[1, 3]"),
        "partition isolation");
    Query count = new Query(any(seq(type("A"), type("B"))), 3, Query.WindowType.COUNT, "$", 0);
    equal(
        ids(
            count,
            List.of(
                event(1, "A", 0, 0, "x"), event(2, "X", 100, 0, "x"), event(3, "B", 1000, 0, "x"))),
        Set.of("[1, 3]"),
        "count window ignores timestamp span");
    Query time = count.withWindow(3);
    time = new Query(time.expression(), 3, Query.WindowType.TIME, "$", 0);
    equal(
        ids(time, List.of(event(1, "A", 0, 0, "x"), event(2, "B", 3, 0, "x"))),
        Set.of(),
        "time boundary");
    SrtEngine consume =
        new SrtEngine(
            List.of(new Query(any(seq(type("A"), type("B"))), 100)),
            new SrtEngine.Options(true, false, 100));
    completed.clear();
    consume.run(types("ABB"), completed::add);
    equal(completed.size(), 1, "consume resets pending runs");
    SrtEngine multiple =
        new SrtEngine(List.of(new Query(type("A"), 100), new Query(type("B"), 100)));
    completed.clear();
    multiple.run(types("AB"), completed::add);
    equal(completed.stream().map(Match::queryIndex).toList(), List.of(0, 1), "multiple queries");
    SrtEngine duplicateIds = new SrtEngine(new Query(any(seq(type("A"), type("B"))), 100));
    completed.clear();
    duplicateIds.run(
        List.of(event(1, "A", 1, 0, "x"), event(1, "A", 2, 0, "x"), event(1, "B", 3, 0, "x")),
        completed::add);
    equal(completed.size(), 2, "event positions distinguish repeated external IDs");
  }

  private static void parserAndFormats() throws Exception {
    String query =
        "#(;(IsEventTypePredicate(A),*(IsEventTypePredicate(B),IsEventTypePredicate(C)),IsEventTypePredicate(D))){window:TIMESTAMP}{windowType:time}";
    equal(
        ids(new SreParser().parse(query, 100).get(0), types("ABCD")),
        Set.of("[1, 2, 3, 4]", "[1, 4]"),
        "multioperand repetition repeats a block");
    equal(
        ids(new SreParser(new PredicateRegistry(), true).parse(query, 100).get(0), types("ABCD")),
        Set.of("[1, 2, 4]", "[1, 4]"),
        "legacy first operand repetition");
    equal(
        new SreParser().parse("TruePredicate & IsEventTypePredicate(B){order:2}").size(),
        2,
        "query separators");
    for (String bad :
        List.of(
            "",
            ";(TruePredicate)",
            "*(TruePredicate)[\"x\"]",
            "IsEventTypePredicate(A){window:TIMESTAMP}",
            "GTAttr(v,\"missing\")",
            ";(TruePredicate[\"x\"],TruePredicate[\"x\"])",
            "FooPredicate",
            "GT(v)",
            "!(TruePredicate)",
            "TruePredicate{windowType:other}",
            "TruePredicate{window:2}{window:3}",
            "TruePredicate{unknown:0}")) {
      failure(
          () -> new SreParser().parse(bad),
          IllegalArgumentException.class,
          "invalid query: " + bad);
    }
    failure(
        () -> CompiledPattern.compile(next(seq(type("A"), seq(type("B"), type("C"))))),
        IllegalArgumentException.class,
        "unsupported composite next");
    PredicateRegistry custom =
        new PredicateRegistry().register("Even", 0, a -> (e, r) -> e.id() % 2 == 0);
    equal(
        ids(new SreParser(custom).parse("Even").get(0), types("AAA")),
        Set.of("[2]"),
        "custom predicate registry");
    equal(
        ids(
            new SreParser()
                .parse("^(|(IsEventTypePredicate(A),IsEventTypePredicate(B)),-GT(v,15.0))")
                .get(0),
            List.of(event(1, "A", 1, 10, "x"), event(2, "B", 2, 20, "x"))),
        Set.of("[1]"),
        "Boolean guards");
    Event stock =
        EventFormats.parse(
            "BUY(price=12.5,timestamp=7,id=2,volume=40,name=INTC)", EventFormats.Domain.STOCK);
    equal(
        stock,
        new Event(2, "BUY", 7, Map.of("price", 12.5, "volume", 40L, "name", "INTC")),
        "named stock fields");
    Event home =
        EventFormats.parse(
            "LOAD(id=1,plug_timestamp=5,value=240.0,property=1,household_id=3)",
            EventFormats.Domain.HOME);
    equal(home.number("householdId"), 3.0, "home adapter");
    Event taxi =
        EventFormats.parse(
            "TRIP(id=3,dropoff_datetime=7,pickup_zone=East"
                + " Harlem/North,dropoff_zone=Midwood,total_amount=8.5)",
            EventFormats.Domain.TAXI);
    equal(taxi.attribute("pickupZone"), "EastHarlemNorth", "taxi zone normalization");
    Event json =
        EventFormats.parse(
            "{\"id\":1,\"type\":\"A\",\"timestamp\":5,\"attributes\":{\"v\":2}}",
            EventFormats.Domain.JSON);
    equal(json.id(), 1L, "JSON adapter");
    failure(
        () ->
            EventFormats.parse(
                "{\"id\":1.5,\"type\":\"A\",\"timestamp\":5,\"attributes\":{}}",
                EventFormats.Domain.JSON),
        IllegalArgumentException.class,
        "fractional ID rejected");
    failure(
        () ->
            EventFormats.parse(
                "BUY(id=1,name=X,volume=1,price=NaN,timestamp=1)", EventFormats.Domain.STOCK),
        IllegalArgumentException.class,
        "nonfinite input rejected");
    failure(
        () ->
            EventFormats.parse(
                "BUY(id=1,id=2,name=X,volume=1,price=1,timestamp=1)", EventFormats.Domain.STOCK),
        IllegalArgumentException.class,
        "duplicate fields rejected");
    try (EventSource source =
        new EventSource(
            Path.of("test-data/cer-srt/examples/stocks.stream"), EventFormats.Domain.STOCK)) {
      int count = 0;
      for (Event ignored : source) count++;
      equal(count, 3, "lazy native file input");
      failure(source::iterator, IllegalStateException.class, "input is single-use");
    }
    Path malformed = Files.createTempFile("cer-invalid", ".stream");
    try {
      Files.writeString(malformed, "\n// comment\ninvalid\n");
      try (EventSource source = new EventSource(malformed, EventFormats.Domain.STOCK)) {
        try {
          source.iterator().hasNext();
          throw new AssertionError("Expected parse failure");
        } catch (IllegalArgumentException error) {
          equal(error.getMessage().contains(":3:"), true, "input line diagnostic");
        }
      }
    } finally {
      Files.delete(malformed);
    }
  }

  private static void streaming() {
    SrtEngine engine = new SrtEngine(new Query(any(seq(type("A"), type("B"))), 3));
    engine.accept(event(1, "A", 1, 0, "x"));
    engine.accept(event(2, "X", 4, 0, "x"));
    equal(engine.statistics().activeRuns(), 0, "expired run cleanup");
    failure(
        () -> engine.accept(event(3, "B", 3, 0, "x")),
        IllegalArgumentException.class,
        "out-of-order input");
    equal(engine.statistics().events(), 2L, "invalid ordering leaves position unchanged");
    engine.reset();
    equal(engine.statistics().activeRuns(), 0, "explicit reset");
    SrtEngine partition =
        new SrtEngine(
            new Query(any(seq(type("A"), type("B"))), 3, Query.WindowType.TIME, "key", 0));
    partition.accept(event(1, "A", 1, 0, "idle"));
    partition.accept(event(2, "X", 4, 0, "other"));
    equal(partition.statistics().activeRuns(), 0, "inactive partition cleanup");
    SrtEngine bounded =
        new SrtEngine(
            List.of(new Query(any(seq(type("A"), type("B"))), 100)),
            new SrtEngine.Options(false, false, 2));
    bounded.accept(event(1, "A", 1, 0, "x"));
    bounded.accept(event(2, "A", 2, 0, "x"));
    failure(
        () -> bounded.accept(event(3, "A", 3, 0, "x")),
        IllegalStateException.class,
        "run cap fails explicitly");
    SrtEngine callbacks = new SrtEngine(new Query(type("A"), 0));
    callbacks.accept(
        event(1, "A", 1, 0, "x"),
        m ->
            failure(
                () -> callbacks.accept(event(2, "A", 2, 0, "x")),
                IllegalStateException.class,
                "callback reentry rejected"));
    equal(callbacks.statistics().matches(), 1L, "streaming output count");
    SrtEngine large = new SrtEngine(new Query(any(seq(type("A"), type("B"))), 3));
    for (int i = 0; i < 10000; i++) large.accept(event(i, "A", i, 0, "x"), ignored -> {});
    equal(large.statistics().peakActiveRuns() <= 3, true, "window bounds long stream memory");
    SrtEngine extreme = new SrtEngine(new Query(any(seq(type("A"), type("B"))), 10));
    extreme.accept(event(1, "A", Long.MIN_VALUE, 0, "x"));
    equal(
        extreme.accept(event(2, "B", Long.MAX_VALUE, 0, "x")).size(),
        0,
        "timestamp subtraction overflow");
  }

  private record Result(int end, Map<String, Event> registers, List<Integer> positions) {}

  // This interpreter enumerates finite words directly, without constructing automata or closures.
  private static List<Result> interpret(
      Expression expression,
      Expression.Selection selection,
      int start,
      Map<String, Event> registers,
      List<Event> input) {
    if (expression instanceof Expression.Selected selected)
      return interpret(selected.child(), selected.selection(), start, registers, input);
    if (expression instanceof Expression.Epsilon)
      return List.of(new Result(start, registers, List.of()));
    if (expression instanceof Expression.Atom atom) {
      if (start == input.size() || !atom.guard().test(input.get(start), registers))
        return List.of();
      Map<String, Event> updated = registers;
      if (atom.register() != null) {
        updated = new LinkedHashMap<>(registers);
        updated.put(atom.register(), input.get(start));
      }
      return List.of(new Result(start + 1, updated, List.of(start)));
    }
    if (expression instanceof Expression.Choice choice) {
      List<Result> results = new ArrayList<>();
      for (Expression child : choice.children())
        results.addAll(interpret(child, Expression.Selection.STRICT, start, registers, input));
      return results;
    }
    if (expression instanceof Expression.Sequence sequence) {
      List<Result> partial = List.of(new Result(start, registers, List.of()));
      for (int i = 0; i < sequence.children().size(); i++) {
        List<Result> next = new ArrayList<>();
        for (Result prefix : partial) {
          int last = i > 0 && selection == Expression.Selection.ANY ? input.size() : prefix.end;
          for (int at = prefix.end; at <= last; at++) {
            for (Result suffix :
                interpret(
                    sequence.children().get(i),
                    Expression.Selection.STRICT,
                    at,
                    prefix.registers,
                    input)) next.add(combine(prefix, suffix));
          }
        }
        partial = next;
      }
      return partial;
    }
    if (expression instanceof Expression.Repeat repeat) {
      List<Result> result = new ArrayList<>();
      repeat(repeat.child(), selection, new Result(start, registers, List.of()), input, result, 0);
      return result;
    }
    throw new AssertionError(expression);
  }

  private static void repeat(
      Expression child,
      Expression.Selection selection,
      Result prefix,
      List<Event> input,
      List<Result> results,
      int depth) {
    results.add(prefix);
    int last = depth > 0 && selection == Expression.Selection.ANY ? input.size() : prefix.end;
    for (int at = prefix.end; at <= last; at++) {
      for (Result suffix :
          interpret(child, Expression.Selection.STRICT, at, prefix.registers, input)) {
        if (suffix.end > at)
          repeat(child, selection, combine(prefix, suffix), input, results, depth + 1);
      }
    }
  }

  private static Result combine(Result prefix, Result suffix) {
    List<Integer> positions = new ArrayList<>(prefix.positions);
    positions.addAll(suffix.positions);
    return new Result(suffix.end, suffix.registers, positions);
  }

  private static Set<String> reference(Query query, List<Event> input) {
    if (!query.partitionBy().equals("$")) {
      Map<Object, List<Integer>> partitions = new LinkedHashMap<>();
      for (int i = 0; i < input.size(); i++)
        partitions
            .computeIfAbsent(
                input.get(i).attribute(query.partitionBy()), ignored -> new ArrayList<>())
            .add(i);
      Set<String> result = new HashSet<>();
      for (List<Integer> positions : partitions.values())
        result.addAll(reference(query, positions.stream().map(input::get).toList(), positions));
      return result;
    }
    return reference(
        query, input, java.util.stream.IntStream.range(0, input.size()).boxed().toList());
  }

  private static Set<String> reference(
      Query query, List<Event> input, List<Integer> globalPositions) {
    Set<String> matches = new HashSet<>();
    for (int start = 0; start < input.size(); start++) {
      for (Result result :
          interpret(query.expression(), Expression.Selection.STRICT, start, Map.of(), input)) {
        if (result.positions.isEmpty()) continue;
        int first = result.positions.get(0),
            last = result.positions.get(result.positions.size() - 1);
        if (last != result.end - 1) continue;
        long span =
            query.windowType() == Query.WindowType.COUNT
                ? globalPositions.get(last) - globalPositions.get(first)
                : input.get(last).timestamp() - input.get(first).timestamp();
        if (query.window() != 0 && span >= query.window()) continue;
        matches.add(result.positions.stream().map(i -> input.get(i).id()).toList().toString());
      }
    }
    return matches;
  }

  static void verifyWithOracle(Query query, List<Event> events, String label) {
    equal(ids(query, events), reference(query, events), label);
  }

  private static void differential() {
    Random random = new Random(20261002);
    Guard a = (e, r) -> e.type().equals("A");
    Guard higher =
        (e, r) ->
            e.type().equals("C") && r.containsKey("x") && e.number("v") > r.get("x").number("v");
    List<Expression> patterns =
        List.of(
            seq(type("A"), type("B"), type("C")),
            any(seq(type("A"), type("B"), type("C"))),
            any(seq(type("A"), star(type("B")), type("C"))),
            any(seq(type("A"), any(star(type("B"))), type("C"))),
            any(seq(type("A"), star(seq(type("B"), type("C"))), type("D"))),
            any(seq(type("A"), any(star(seq(type("B"), type("C")))), type("D"))),
            star(choice(type("A"), type("B"))),
            any(star(type("A"))),
            any(seq(atom(a, "x"), star(type("B")), atom(higher))),
            any(seq(choice(type("A"), type("B")), choice(type("C"), type("D")))),
            any(seq(type("A"), star(star(type("B"))), type("C"))));
    for (int trial = 0; trial < 60; trial++) {
      List<Event> input = new ArrayList<>();
      for (int i = 0; i < 7; i++)
        input.add(
            event(
                i + 1,
                String.valueOf("ABCD".charAt(random.nextInt(4))),
                i / 2,
                random.nextInt(10),
                "x"));
      for (Expression expression : patterns) {
        Query query =
            new Query(
                expression,
                2 + random.nextInt(6),
                random.nextBoolean() ? Query.WindowType.COUNT : Query.WindowType.TIME,
                "$",
                0);
        equal(
            ids(query, input),
            reference(query, input),
            "independent expression oracle trial " + trial);
      }
    }
  }

  private static Set<String> ids(Query query, List<Event> input) {
    Set<String> ids = new HashSet<>();
    SrtEngine engine = new SrtEngine(query);
    engine.run(
        input,
        m -> {
          if (!ids.add(m.eventIds().toString())) throw new AssertionError("Duplicate group: " + m);
        });
    return ids;
  }

  private static List<Event> types(String text) {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < text.length(); i++)
      events.add(event(i + 1, text.substring(i, i + 1), i + 1, 0, "x"));
    return events;
  }

  private static Event event(long id, String type, long timestamp, double value, String key) {
    return new Event(id, type, timestamp, Map.of("v", value, "key", key));
  }

  private static void equal(Object actual, Object expected, String label) {
    if (!actual.equals(expected))
      throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    checks++;
  }

  private static void failure(Runnable action, Class<? extends Throwable> type, String label) {
    try {
      action.run();
    } catch (Throwable error) {
      if (type.isInstance(error)) {
        checks++;
        return;
      }
      throw new AssertionError(label, error);
    }
    throw new AssertionError("Expected failure: " + label);
  }
}
