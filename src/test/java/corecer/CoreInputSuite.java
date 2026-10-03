package corecer;

import corecer.runtime.events.Event;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.json.JSONObject;

/** Input and CLI integration checks using small temporary files. */
public final class CoreInputSuite {
  private static int checks;

  private CoreInputSuite() {}

  public static void main(String[] args) throws Exception {
    Path folder = Files.createTempDirectory("core-input-suite-");
    try {
      // This suite's input schema is intentionally independent of the demonstration files.
      String declarations = "DECLARE EVENT A(id int, key string, v double, tick long)\n"
          + "DECLARE EVENT B(id int, key string, v double, tick long)\n"
          + "DECLARE STREAM S(A,B)\nDECLARE STREAM S2(A,B)";
      Path schema = write(folder, "schema.core", declarations);
      Path query = write(folder, "query.core",
          "SELECT ALL * FROM S WHERE A; B WITHIN 100 EVENTS CONSUME BY NONE");
      Path csv = write(folder, "events.csv", "event,time,id,key,v,tick\n"
          + "A,100,0,\"a,b\",1.5,0\nB,200,1,\"a,b\",2.5,1\n"
          + "A,300,2,\"a\"\"b\",3.5,2\nB,400,3,\"a\"\"b\",4.5,3\n");
      Path events = write(folder, "events.data", "A(tick=0,v=1.5,key='a,b',id=0)\n"
          + "B(id=1,key='a,b',v=2.5,tick=1)\n"
          + "A(id=2,key='a''b',v=3.5,tick=2)\nB(id=3,key='a''b',v=4.5,tick=3)\n");
      loaders(folder, declarations, csv, events);
      cli(folder, schema, query, csv);
    } finally {
      try (var paths = Files.walk(folder)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
      }
    }
    System.out.println("CORE input/CLI suite: " + checks + " checks passed");
  }

  private static void loaders(Path folder, String declarations, Path csv, Path events) throws Exception {
    String query = "SELECT ALL * FROM S WHERE A; B WITHIN 100 EVENTS CONSUME BY NONE";
    try (CoreSession ignored = new CoreSession(declarations, query, null)) {
      List<Event> loaded = CoreInputs.load(List.of(source(csv, "S", CoreInputs.Format.CSV)), -1).events();
      equal(loaded.size(), 4, "CSV event count");
      equal(loaded.get(0).getValue("key"), "a,b", "quoted CSV delimiter");
      equal(loaded.get(2).getValue("key"), "a\"b", "doubled CSV quote");
      List<Event> named = CoreInputs.load(List.of(source(events, "S", CoreInputs.Format.EVENTS)), -1).events();
      equal(named.get(0).getValue("key"), "a,b", "single quoted named attribute");
      equal(named.get(2).getValue("key"), "a'b", "escaped named attribute quote");
      equal(named.stream().map(Event::getTimestamp).toList(), List.of(0L, 1L, 2L, 3L),
          "missing event timestamps use row position");
      Path apostrophes = write(folder, "apostrophes.stream",
          "A(id=0,key=Prince's Bay,v=1.5,tick=0)\n"
          + "B(id=1,key=\"Prince's Bay\",v=2.5,tick=1)\n");
      List<Event> zones = CoreInputs.load(
          List.of(source(apostrophes, "S", CoreInputs.Format.EVENTS)), -1).events();
      equal(zones.get(0).getValue("key"), "Prince's Bay", "unquoted native taxi zone apostrophe");
      equal(zones.get(1).getValue("key"), "Prince's Bay", "quoted value with an internal apostrophe");
      equal(CoreInputs.timestamp("2020-01-02 03:04:05"),
          Instant.parse("2020-01-02T03:04:05Z").toEpochMilli(), "UTC timestamp interpretation");
      Path s1 = write(folder, "first.csv", "event,time,id,key,v,tick\nA,100,0,x,1,0\nB,300,1,x,2,1\n");
      Path s2 = write(folder, "second.csv", "event,time,id,key,v,tick\nA,100,2,x,3,2\nB,200,3,x,4,3\n");
      List<CoreInputs.Source> sources = List.of(source(s1, "S", CoreInputs.Format.CSV), source(s2, "S2", CoreInputs.Format.CSV));
      var merged = CoreInputs.load(sources, -1);
      equal(merged.events().stream().map(Event::getStreamName).toList(), List.of("S", "S2", "S2", "S"),
          "multiple streams merge stably by timestamp");
      var capped = CoreInputs.load(sources, 2);
      equal(capped.events().stream().map(Event::getStreamName).toList(), List.of("S", "S2"),
          "global limit follows merged order");
      Path input = write(folder, "bad.csv", "event,time,id,key,v,tick\nA,200,0,x,1,0\nB,100,1,x,2,1\n");
      try {
        CoreInputs.load(List.of(source(input, "S", CoreInputs.Format.CSV)), -1);
        throw new AssertionError("decreasing timestamps accepted");
      } catch (IllegalArgumentException failure) {
        equal(failure.getMessage().contains("bad.csv:3:"), true, "input error gives file and row");
      }
    }
  }

  private static void cli(Path folder, Path schema, Path query, Path csv) throws Exception {
    String[] direct = {"--declarations", schema.toString(), "--query", query.toString(), "--events", csv.toString()};
    JSONObject result = CoreMain.run(direct);
    equal(result.getLong("events"), 4L, "CLI event count");
    equal(result.getLong("matches"), 3L, "CLI enumerates every sequence");
    equal(result.getBoolean("matchesComplete"), true, "full enumeration completeness");
    Path output = folder.resolve("matches.jsonl");
    result = CoreMain.run(concat(direct, "--output", output.toString()));
    equal(Files.readAllLines(output).stream()
        .map(line -> new JSONObject(line).getJSONArray("eventIndices").toList().toString()).sorted().toList(),
        List.of("[0, 1]", "[0, 3]", "[2, 3]"), "JSONL contains detached match indices");
    result = CoreMain.run(concat(direct, "--no-enumeration"));
    equal(result.isNull("matches"), true, "recognition-only match count is unknown");
    equal(result.getLong("triggers"), 2L, "recognition-only trigger count");
    result = CoreMain.run(concat(direct, "--max-events", "0"));
    equal(result.getLong("events"), 0L, "modern CLI zero means zero events");
    Path descriptor = write(folder, "original-query.txt", "FILE:schema.core\nFILE:query.core\n");
    Path streams = write(folder, "original-streams.txt", "S:FILE:events.data\n");
    result = CoreMain.run(new String[] {"-q", descriptor.toString(), "-s", streams.toString(), "-of", "-f"});
    equal(result.getLong("matches"), 3L, "original named event stream matches");
    String originalInput = Files.readString(csv);
    fails(() -> CoreMain.run(concat(direct, "--output", csv.toString())), "output cannot replace an input");
    equal(Files.readString(csv), originalInput, "input remains intact after output path rejection");
    fails(() -> CoreMain.run(concat(direct, "--no-enumeration", "--output", output.toString())),
        "reject contradictory output mode");
  }

  private static Path write(Path folder, String name, String contents) throws Exception {
    Path path = folder.resolve(name);
    Files.writeString(path, contents);
    return path;
  }

  private static CoreInputs.Source source(Path path, String stream, CoreInputs.Format format) {
    return new CoreInputs.Source(path, stream, format);
  }

  private static String[] concat(String[] existing, String... extra) {
    String[] result = new String[existing.length + extra.length];
    System.arraycopy(existing, 0, result, 0, existing.length);
    System.arraycopy(extra, 0, result, existing.length, extra.length);
    return result;
  }

  private static void equal(Object actual, Object expected, String description) {
    CoreParitySuite.equal(actual, expected, description);
    checks++;
  }

  @FunctionalInterface
  private interface Action { void run() throws Exception; }

  private static void fails(Action action, String description) throws Exception {
    try {
      action.run();
    } catch (IllegalArgumentException failure) {
      checks++;
      return;
    }
    throw new AssertionError(description + ": accepted invalid input");
  }
}
