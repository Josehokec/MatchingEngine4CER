package corecer;

import corecer.execution.BaseExecutor;
import corecer.execution.structures.output.ComplexEvent;
import corecer.parser.CompoundStatementParser;
import corecer.parser.DeclarationParser;
import corecer.parser.QueryParser;
import corecer.runtime.events.Event;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Saved short-stream results, also executable against the original upstream classpath. */
public final class CoreParitySuite {
  private CoreParitySuite() {}

  public static void main(String[] args) throws Exception {
    Path fixturePath = Path.of(args.length == 0 ? "test-data/core-cer/parity/fixtures.json" : args[0]);
    JSONObject source = new JSONObject(Files.readString(fixturePath));
    new CompoundStatementParser(new DeclarationParser(), new QueryParser())
        .parse(source.getString("declarations"));
    JSONArray fixtures = source.getJSONArray("fixtures");
    boolean capture = args.length > 1 && args[1].equals("--capture");
    for (int i = 0; i < fixtures.length(); i++) {
      JSONObject fixture = fixtures.getJSONObject(i);
      List<List<Long>> actual = run(fixture);
      if (capture) {
        fixture.put("upstreamExpected", new JSONArray(actual));
      } else {
        equal(actual, expected(fixture.getJSONArray("expected")), fixture.getString("name"));
        if (fixture.has("upstreamExpected")) {
          equal(actual, expected(fixture.getJSONArray("upstreamExpected")),
              fixture.getString("name") + " upstream parity");
        }
      }
    }
    if (capture) {
      System.out.println(source.toString(2));
    } else {
      System.out.println("CORE parity suite: " + fixtures.length() + " short-stream fixtures passed");
    }
  }

  static List<List<Long>> run(JSONObject fixture) throws Exception {
    BaseExecutor executor = BaseExecutor.fromPlan(new QueryParser().parse(fixture.getString("query")));
    String types = fixture.getString("types");
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < types.length(); i++) {
      events.add(Event.EventWithTimestamp("S", types.substring(i, i + 1),
          numberAt(fixture, "timestamps", i, i * 100L), i,
          stringAt(fixture, "keys", i, "x"),
          (double) numberAt(fixture, "values", i, i + 1),
          numberAt(fixture, "ticks", i, i)));
    }
    long origin = events.isEmpty() ? 0 : events.get(0).getIndex();
    List<List<Long>> actual = new ArrayList<>();
    executor.setMatchCallback(grouping -> {
      for (ComplexEvent match : grouping) {
        // Both grouping and ComplexEvent are lazy, mutable, and reused by the upstream iterator.
        if (match == null) continue;
        List<Long> indices = new ArrayList<>();
        for (Event event : match) {
          if (event.getType() >= 0) indices.add(event.getIndex() - origin);
        }
        actual.add(List.copyOf(indices));
      }
    });
    for (Event event : events) executor.sendEvent(event);
    return sorted(actual);
  }

  private static long numberAt(JSONObject object, String field, int index, long fallback) {
    return object.has(field) ? object.getJSONArray(field).getLong(index) : fallback;
  }

  private static String stringAt(JSONObject object, String field, int index, String fallback) {
    return object.has(field) ? object.getJSONArray(field).getString(index) : fallback;
  }

  static List<List<Long>> expected(JSONArray json) {
    List<List<Long>> out = new ArrayList<>();
    for (int i = 0; i < json.length(); i++) {
      JSONArray row = json.getJSONArray(i);
      List<Long> indices = new ArrayList<>();
      for (int j = 0; j < row.length(); j++) indices.add(row.getLong(j));
      out.add(List.copyOf(indices));
    }
    return sorted(out);
  }

  static List<List<Long>> sorted(List<List<Long>> matches) {
    List<List<Long>> out = new ArrayList<>(matches);
    out.sort(Comparator.comparing(Object::toString));
    return out;
  }

  static void equal(Object actual, Object expected, String description) {
    if (!actual.equals(expected)) {
      throw new AssertionError(description + ": expected " + expected + ", got " + actual);
    }
  }
}
