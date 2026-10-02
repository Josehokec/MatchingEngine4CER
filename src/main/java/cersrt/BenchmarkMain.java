package cersrt;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** A portable Java replacement for the duplicated per-domain Python experiment loops. */
public final class BenchmarkMain {
  private BenchmarkMain() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2)
      throw new IllegalArgumentException(
          "Usage: cersrt.BenchmarkMain query.sre-or-directory stream [--domain"
              + " stock|home|taxi|json] [--windows 500,1000] [--iterations 3] [--output"
              + " results.csv] [--legacy] [--consume] [--limit 1000]");
    Path queryPath = Path.of(args[0]);
    Path stream = Path.of(args[1]);
    Path output = null;
    EventFormats.Domain domain = EventFormats.Domain.STOCK;
    List<Long> windows = List.of(500L, 1000L, 2000L, 5000L, 10000L, 30000L);
    int iterations = 3;
    long limit = Long.MAX_VALUE;
    boolean legacy = false;
    boolean consume = false;
    for (int i = 2; i < args.length; i++) {
      switch (args[i]) {
        case "--domain" -> domain = EventFormats.domain(value(args, ++i));
        case "--windows" -> windows =
            Arrays.stream(value(args, ++i).split(",")).map(Long::valueOf).toList();
        case "--iterations" -> iterations = Integer.parseInt(value(args, ++i));
        case "--output" -> output = Path.of(value(args, ++i));
        case "--limit" -> limit = Long.parseLong(value(args, ++i));
        case "--legacy" -> legacy = true;
        case "--consume" -> consume = true;
        default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
      }
    }
    if (iterations < 1 || limit < 0 || windows.isEmpty() || windows.stream().anyMatch(w -> w < 0))
      throw new IllegalArgumentException("Invalid iteration, event limit, or window");
    List<Path> paths;
    if (Files.isDirectory(queryPath)) {
      try (var walk = Files.walk(queryPath)) {
        paths = walk.filter(p -> p.toString().endsWith(".sre")).sorted().toList();
      }
    } else paths = List.of(queryPath);
    if (paths.isEmpty()) throw new IllegalArgumentException("No .sre queries found");
    if (output != null) {
      Path absolute = output.toAbsolutePath().normalize();
      if (absolute.equals(stream.toAbsolutePath().normalize())
          || paths.stream().anyMatch(p -> absolute.equals(p.toAbsolutePath().normalize())))
        throw new IllegalArgumentException("Output must differ from input files");
      if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
    }
    PrintWriter writer =
        output == null
            ? new PrintWriter(System.out)
            : new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8));
    try {
      writer.println(
          "query,window,iteration,events,matches,seconds,eventsPerSecond,peakActiveRuns,heapUsedBytes");
      for (Path path : paths) {
        String text = Files.readString(path);
        for (long window : windows) {
          List<Query> queries =
              new SreParser(new PredicateRegistry(), legacy)
                  .parse(text, window).stream().map(q -> q.withWindow(window)).toList();
          for (int iteration = 1; iteration <= iterations; iteration++) {
            SrtEngine engine =
                new SrtEngine(queries, new SrtEngine.Options(consume, legacy, 1_000_000));
            long count = 0;
            long started = System.nanoTime();
            try (EventSource source = new EventSource(stream, domain)) {
              var input = source.iterator();
              while (count < limit && input.hasNext()) {
                engine.accept(input.next(), ignored -> {});
                count++;
              }
            }
            double seconds = (System.nanoTime() - started) / 1e9;
            long usedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            writer.printf(
                Locale.ROOT,
                "\"%s\",%d,%d,%d,%d,%.9f,%.3f,%d,%d%n",
                path.toString().replace("\"", "\"\""),
                window,
                iteration,
                count,
                engine.statistics().matches(),
                seconds,
                count / seconds,
                engine.statistics().peakActiveRuns(),
                usedHeap);
            writer.flush();
            if (writer.checkError())
              throw new java.io.IOException("Could not write benchmark results");
          }
        }
      }
    } finally {
      if (output != null) writer.close();
      else writer.flush();
    }
  }

  private static String value(String[] args, int index) {
    if (index == args.length) throw new IllegalArgumentException("Missing option value");
    return args[index];
  }
}
