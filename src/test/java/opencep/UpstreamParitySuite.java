package opencep;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import opencep.adaptive.*;
import opencep.base.*;
import opencep.condition.*;
import opencep.misc.*;
import opencep.plan.*;
import opencep.plugin.sensors.SensorsDataFormatter;
import opencep.plugin.stocks.MetastockDataFormatter;
import opencep.stream.OutputStream;

/** Runs saved Python-engine results with no Python runtime dependency. */
public final class UpstreamParitySuite {
  private static Map<String, Object> map(Object o) {
    return FixtureExpressions.map(o);
  }

  private static List<Object> list(Object o) {
    return FixtureExpressions.list(o);
  }

  private static Integer integer(Object o) {
    return o == null ? null : ((Number) o).intValue();
  }

  private static Double decimal(Object o) {
    return o == null ? null : ((Number) o).doubleValue();
  }

  private static Function<Map<String, Object>, Object> getter(Object o) {
    return payload -> FixtureExpressions.call(map(o), List.of(payload));
  }

  private static Object term(Object o) {
    var t = map(o);
    return t.containsKey("variable")
        ? new Variable(t.get("variable").toString(), getter(t.get("getter")))
        : t.get("constant");
  }

  private static Condition condition(Object o) {
    var c = map(o);
    String op = (String) c.get("op");
    return switch (op) {
      case "TRUE" -> new TrueCondition();
      case "AND", "OR" -> {
        var args =
            list(c.get("args")).stream()
                .map(UpstreamParitySuite::condition)
                .toArray(Condition[]::new);
        yield op.equals("AND") ? new AndCondition(args) : new OrCondition(args);
      }
      case "RELATION" -> {
        Object left = term(c.get("left")), right = term(c.get("right"));
        yield switch (c.get("relation").toString()) {
          case "EqCondition" -> new EqCondition(left, right);
          case "NotEqCondition" -> new NotEqCondition(left, right);
          case "GreaterThanCondition" -> new GreaterThanCondition(left, right);
          case "GreaterThanEqCondition" -> new GreaterThanEqCondition(left, right);
          case "SmallerThanCondition" -> new SmallerThanCondition(left, right);
          case "SmallerThanEqCondition" -> new SmallerThanEqCondition(left, right);
          default -> throw new IllegalArgumentException("Unknown relation " + c);
        };
      }
      case "SIMPLE" -> new SimpleCondition(
          values ->
              FixtureExpressions.truth(FixtureExpressions.call(map(c.get("relation")), values)),
          list(c.get("terms")).stream().map(UpstreamParitySuite::term).toArray());
      case "KCINDEX", "KCVALUE" -> {
        var names = new HashSet<String>();
        list(c.get("names")).forEach(n -> names.add(n.toString()));
        BiPredicate<Object, Object> relation =
            (a, b) ->
                FixtureExpressions.truth(
                    FixtureExpressions.call(map(c.get("relation")), List.of(a, b)));
        yield op.equals("KCINDEX")
            ? new KCIndexCondition(
                names,
                getter(c.get("getter")),
                relation,
                integer(c.get("first")),
                integer(c.get("second")),
                integer(c.get("offset")))
            : new KCValueCondition(
                names, getter(c.get("getter")), relation, c.get("value"), integer(c.get("index")));
      }
      default -> throw new IllegalArgumentException("Unknown condition " + c);
    };
  }

  private static PatternStructure structure(Object o) {
    var s = map(o);
    String op = (String) s.get("op");
    if (op.equals("LEAF"))
      return new PrimitiveEventStructure(s.get("type").toString(), s.get("name").toString());
    var args =
        list(s.get("args")).stream()
            .map(UpstreamParitySuite::structure)
            .toArray(PatternStructure[]::new);
    return switch (op) {
      case "SEQ" -> new SeqOperator(args);
      case "AND" -> new AndOperator(args);
      case "OR" -> new OrOperator(args);
      case "NOT" -> new NegationOperator(args[0]);
      case "KC" -> new KleeneClosureOperator(args[0], integer(s.get("min")), integer(s.get("max")));
      default -> throw new IllegalArgumentException("Unknown structure " + s);
    };
  }

  private static Pattern pattern(Object o) {
    var p = map(o);
    ConsumptionPolicy policy = new ConsumptionPolicy();
    if (p.get("policy") != null) {
      var c = map(p.get("policy"));
      Set<String> types = null;
      if (c.get("singleTypes") != null) {
        types = new HashSet<>();
        for (var t : list(c.get("singleTypes"))) types.add(t.toString());
      }
      var contiguous =
          list(c.get("contiguous")).stream()
              .map(l -> list(l).stream().map(Object::toString).toList())
              .toList();
      var freeze = new HashSet<String>();
      list(c.get("freeze")).forEach(n -> freeze.add(n.toString()));
      policy =
          new ConsumptionPolicy(
              SelectionStrategies.valueOf(c.get("strategy").toString()), types, contiguous, freeze);
    }
    var pattern =
        new Pattern(
            structure(p.get("structure")),
            condition(p.get("condition")),
            Duration.ofNanos((long) (decimal(p.get("window")) * 1e9)),
            policy,
            integer(p.get("id")),
            decimal(p.get("confidence")));
    if (p.get("statistics") instanceof Map<?, ?> statistics && statistics.get("rates") != null) {
      var rates =
          list(statistics.get("rates")).stream().mapToDouble(FixtureExpressions::number).toArray();
      double[][] matrix =
          statistics.get("matrix") == null
              ? null
              : list(statistics.get("matrix")).stream()
                  .map(row -> list(row).stream().mapToDouble(FixtureExpressions::number).toArray())
                  .toArray(double[][]::new);
      pattern.setStatistics(new StatisticsSnapshot(rates, matrix));
    }
    return pattern;
  }

  private record Normalized(List<List<Long>> events, List<Integer> ids, Double probability) {
    String key() {
      return events + "/" + ids;
    }
  }

  public static void main(String[] args) throws Exception {
    boolean rawInput = Arrays.asList(args).contains("--raw");
    var fixtures = list(Json.parse(Files.readString(Path.of("test-data/upstream-cases.json"))));
    int passed = 0;
    var failures = new ArrayList<String>();
    for (var fixture : fixtures) {
      var c = map(fixture);
      String name = c.get("name").toString();
      try {
        var patterns = list(c.get("patterns")).stream().map(UpstreamParitySuite::pattern).toList();
        DataFormatter formatter =
            c.get("formatter").equals("stocks")
                ? new MetastockDataFormatter()
                : new SensorsDataFormatter();
        var events =
            list(c.get("rows")).stream().map(row -> new Event(row.toString(), formatter)).toList();
        long first = events.isEmpty() ? 0 : events.get(0).index;
        var output = new OutputStream<PatternMatch>();
        var defaults = new opencep.evaluation.EvaluationMechanismParameters();
        var compatible =
            new opencep.evaluation.EvaluationMechanismParameters(
                defaults.optimizer(),
                defaults.storage().withLegacySemantics(),
                defaults.updateType(),
                defaults.mergeApproach(),
                defaults.localSearch());
        var cep =
            new CEP(patterns, compatible, new opencep.parallel.ParallelExecutionParameters(), null);
        if (rawInput) {
          var marker = new Event("marker", Instant.EPOCH, Map.of());
          first = marker.index + 1;
          cep.run(list(c.get("rows")).stream().map(Object::toString).toList(), output, formatter);
        } else cep.runEvents(events, output);
        final long origin = first;
        var actual = new ArrayList<Normalized>();
        for (var match : output.snapshot())
          actual.add(
              new Normalized(
                  match.events.stream()
                      .map(e -> e.primitiveEvents().stream().map(v -> v.index - origin).toList())
                      .toList(),
                  match.getPatternIds(),
                  match.probability));
        var expected = new ArrayList<Normalized>();
        for (var entry : list(c.get("expected"))) {
          var e = map(entry);
          expected.add(
              new Normalized(
                  list(e.get("events")).stream()
                      .map(
                          group -> list(group).stream().map(v -> ((Number) v).longValue()).toList())
                      .toList(),
                  list(e.get("ids")).stream().map(UpstreamParitySuite::integer).toList(),
                  decimal(e.get("probability"))));
        }
        Comparator<Normalized> comparator = Comparator.comparing(Normalized::key);
        actual.sort(comparator);
        expected.sort(comparator);
        // AND match order follows the physical plan in upstream, so compare event sets for each
        // aggregate.
        var actualMap = normalize(actual);
        var expectedMap = normalize(expected);
        if (!actualMap.keySet().equals(expectedMap.keySet())
            || actualMap.size() != expectedMap.size()) {
          var missing = new HashSet<>(expectedMap.keySet());
          missing.removeAll(actualMap.keySet());
          var extra = new HashSet<>(actualMap.keySet());
          extra.removeAll(expectedMap.keySet());
          throw new AssertionError(
              "expected="
                  + expected.size()
                  + ", actual="
                  + actual.size()
                  + ", missing="
                  + missing.stream().limit(2).toList()
                  + ", extra="
                  + extra.stream().limit(2).toList());
        }
        for (var key : expectedMap.keySet()) {
          Double a = actualMap.get(key), e = expectedMap.get(key);
          if (a == null ? e != null : e == null || Math.abs(a - e) > 1e-10)
            throw new AssertionError("Probability mismatch " + key + ": " + a + " != " + e);
        }
        passed++;
      } catch (Throwable e) {
        failures.add(name + ": " + e);
      }
    }
    System.out.println(
        "Upstream parity ("
            + (rawInput ? "raw stream" : "Event objects")
            + "): "
            + passed
            + "/"
            + fixtures.size()
            + " fixtures passed");
    failures.forEach(System.err::println);
    if (!failures.isEmpty())
      throw new AssertionError(failures.size() + " upstream parity failures");
  }

  private static Map<String, Double> normalize(List<Normalized> matches) {
    var out = new TreeMap<String, Double>();
    var occurrences = new HashMap<String, Integer>();
    for (var m : matches) {
      var groups =
          m.events.stream()
              .map(group -> group.stream().sorted().toList().toString())
              .sorted()
              .toList();
      var ids = m.ids.isEmpty() ? Collections.singletonList((Integer) null) : m.ids;
      for (var id : ids) {
        String key = groups + "/" + (id == null ? "[]" : "[" + id + "]");
        int occurrence = occurrences.merge(key, 1, Integer::sum);
        if (occurrence > 1) key += "/duplicate=" + occurrence;
        out.put(key, m.probability);
      }
    }
    return out;
  }
}
