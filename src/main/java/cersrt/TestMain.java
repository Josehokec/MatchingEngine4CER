package cersrt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Run ./scripts/run-cer.sh for three self-checking examples, or pass query and stream paths. */
public final class TestMain {
  private TestMain() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      examples();
      return;
    }
    if (args.length < 2)
      throw new IllegalArgumentException(
          "Usage: cersrt.TestMain query.sre events.stream [--domain stock|home|taxi|json] [--window"
              + " 500] [--legacy] [--consume] [--limit 1000]");
    EventFormats.Domain domain = EventFormats.Domain.STOCK;
    long window = 500;
    long limit = Long.MAX_VALUE;
    boolean legacy = false;
    boolean consume = false;
    for (int i = 2; i < args.length; i++) {
      switch (args[i]) {
        case "--domain" -> domain = EventFormats.domain(value(args, ++i));
        case "--window" -> window = Long.parseLong(value(args, ++i));
        case "--limit" -> limit = Long.parseLong(value(args, ++i));
        case "--legacy" -> legacy = true;
        case "--consume" -> consume = true;
        default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
      }
    }
    if (window < 0 || limit < 0)
      throw new IllegalArgumentException("Window and limit must be nonnegative");
    List<Query> queries =
        new SreParser(new PredicateRegistry(), legacy)
            .parse(Files.readString(Path.of(args[0])), window);
    SrtEngine engine = new SrtEngine(queries, new SrtEngine.Options(consume, legacy, 1_000_000));
    long start = System.nanoTime();
    long count = 0;
    try (EventSource source = new EventSource(Path.of(args[1]), domain)) {
      var input = source.iterator();
      while (count < limit && input.hasNext()) {
        engine.accept(input.next(), System.out::println);
        count++;
      }
    }
    System.out.printf(
        "Events=%d matches=%d peakActiveRuns=%d elapsed=%.6fs%n",
        count,
        engine.statistics().matches(),
        engine.statistics().peakActiveRuns(),
        (System.nanoTime() - start) / 1e9);
  }

  private static String value(String[] args, int index) {
    if (index == args.length) throw new IllegalArgumentException("Missing option value");
    return args[index];
  }

  private static void examples() {
    String first = "^(IsEventTypePredicate(SELL),EQStr(name,INTC))[\"x\"]";
    String middle = "^(IsEventTypePredicate(BUY),EQStr(name,RIMM))";
    String last = "^(IsEventTypePredicate(BUY),EQStr(name,QQQ),GTAttr(price,\"x\"))";
    List<Event> events =
        List.of(
            stock(1, "SELL", "INTC", 20), stock(2, "BUY", "RIMM", 25),
            stock(3, "BUY", "RIMM", 26), stock(4, "BUY", "QQQ", 30));
    check("Register comparison", "#(;(" + first + "," + middle + "," + last + "))", events, 2);
    check(
        "Kleene repetition",
        "#(;(" + first + "," + middle + ",#(*(" + middle + "))," + last + "))",
        events,
        3);
    // Each repeated block is a contiguous RIMM;QQQ pair. Unrelated events may separate blocks.
    String nested =
        "#(;(" + first + ",#(*(;(" + middle + "," + last + "))),IsEventTypePredicate(SELL)))";
    List<Event> nestedEvents =
        List.of(
            stock(1, "SELL", "INTC", 20),
            stock(2, "BUY", "RIMM", 25),
            stock(3, "BUY", "QQQ", 30),
            stock(4, "BUY", "OTHER", 1),
            stock(5, "BUY", "RIMM", 26),
            stock(6, "BUY", "QQQ", 31),
            stock(7, "SELL", "END", 10));
    check("Nested block repetition", nested, nestedEvents, 4);
  }

  private static Event stock(long id, String type, String name, double price) {
    return new Event(id, type, id, Map.of("name", name, "price", price, "volume", 100));
  }

  private static void check(String label, String expression, List<Event> events, int expected) {
    Query query = new SreParser().parse(expression + "{window:500}{windowType:time}").get(0);
    SrtEngine engine = new SrtEngine(query);
    List<Match> matches = new ArrayList<>();
    engine.run(events, matches::add);
    if (matches.size() != expected)
      throw new AssertionError(label + ": expected " + expected + ", got " + matches.size());
    System.out.println(label + " (" + expected + " matches)");
    matches.forEach(System.out::println);
  }
}
