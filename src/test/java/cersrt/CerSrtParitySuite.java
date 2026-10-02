package cersrt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import opencep.misc.Json;

/** Compares selected event groups with saved runs of the pinned upstream Wayeb executable. */
public final class CerSrtParitySuite {
  private CerSrtParitySuite() {}

  public static void main(String[] args) throws Exception {
    Path root = Path.of("test-data/cer-srt");
    Object document = Json.parse(Files.readString(root.resolve("upstream-cases.json")));
    if (!(document instanceof List<?> cases)) throw new AssertionError("Invalid cases");
    int passed = 0;
    for (Object item : cases) {
      if (!(item instanceof java.util.Map<?, ?> fixture))
        throw new AssertionError("Invalid fixture");
      String queryPath = fixture.get("query").toString();
      var domain = EventFormats.domain(fixture.get("domain").toString());
      long window = ((Number) fixture.get("window")).longValue();
      String queryText = Files.readString(root.resolve(queryPath));
      List<Query> queries = new SreParser(new PredicateRegistry(), true).parse(queryText, window);
      SrtEngine engine = new SrtEngine(queries, SrtEngine.Options.legacy());
      List<String> actual = new ArrayList<>();
      List<Event> events = new ArrayList<>();
      for (Object line : (List<?>) fixture.get("lines"))
        events.add(EventFormats.parse(line.toString(), domain));
      engine.run(events, m -> actual.add(m.eventIds().toString()));
      List<String> expected = new ArrayList<>();
      for (Object group : (List<?>) fixture.get("matches")) {
        expected.add(
            ((List<?>) group).stream().map(id -> ((Number) id).longValue()).toList().toString());
      }
      actual.sort(String::compareTo);
      expected.sort(String::compareTo);
      if (!actual.equals(expected))
        throw new AssertionError(queryPath + ": expected " + expected + ", got " + actual);
      // Independently enumerate the corrected expression, including nested multioperand stars.
      for (Query corrected : new SreParser().parse(queryText, window))
        CerSrtSuite.verifyWithOracle(corrected, events, queryPath + " corrected semantics");
      passed++;
    }
    if (passed != 43) throw new AssertionError("Expected all 43 published Wayeb query templates");
    System.out.println(
        "CER-SRT upstream parity: "
            + passed
            + "/"
            + cases.size()
            + " cases passed; corrected semantics also match the independent interpreter");
  }
}
