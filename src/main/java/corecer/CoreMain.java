package corecer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Portable offline CORE baseline entry point; stdout contains one JSON metrics record. */
public final class CoreMain {
  private CoreMain() {}

  public static void main(String[] args) {
    try {
      JSONObject metrics = run(args);
      if (metrics != null) System.out.println(metrics);
    } catch (Exception failure) {
      System.err.println("CORE: " + failure.getMessage());
      System.exit(2);
    }
  }

  /** Runs the same adapter as the CLI, returning metrics without writing them to stdout. */
  public static JSONObject run(String[] args) throws Exception {
    Configuration config = arguments(args);
    if (config.help) {
      System.out.println(usage());
      return null;
    }
    Inputs inputs = inputs(config);
    validateOutput(config.output, inputs);
    String declarations = Files.readString(inputs.declarations, StandardCharsets.UTF_8);
    String queries = Files.readString(inputs.query, StandardCharsets.UTF_8);
    var options = new CoreSession.Options(config.enumerate, config.enumerationLimit);
    BufferedWriter output = null;
    try {
      // Open only after input paths have been checked; output is streamed and never retained.
      if (config.output != null) {
        Path parent = config.output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        output = Files.newBufferedWriter(config.output, StandardCharsets.UTF_8);
      }
      final BufferedWriter matchOutput = output;
      Consumer<CoreSession.Match> listener = matchOutput == null ? null : match -> {
        JSONObject record = new JSONObject();
        record.put("query", match.queryNumber());
        record.put("triggerIndex", match.triggerIndex());
        record.put("eventIndices", match.eventIndices());
        try {
          matchOutput.write(record.toString());
          matchOutput.newLine();
        } catch (IOException failure) {
          throw new UncheckedIOException(failure);
        }
      };
      try (CoreSession session = new CoreSession(declarations, queries, options, listener)) {
        long loadStart = System.nanoTime();
        CoreInputs.Loaded loaded = CoreInputs.load(inputs.sources, config.maxEvents);
        long loadNanos = System.nanoTime() - loadStart;
        long runStart = System.nanoTime();
        session.setTimeoutSeconds(config.timeout);
        for (var event : loaded.events()) {
          if (session.timedOut()) break;
          session.sendEvent(event);
        }
        long runNanos = System.nanoTime() - runStart;
        CoreSession.Metrics stats = session.metrics();
        if (output != null) output.flush();
        JSONObject result = new JSONObject();
        result.put("engine", "CORE-CER");
        result.put("queries", session.queryCount());
        result.put("compileSeconds", seconds(session.compileNanos()));
        result.put("loadSeconds", seconds(loadNanos));
        result.put("processSeconds", seconds(stats.processNanos()));
        result.put("enumerationSeconds", seconds(stats.enumerationNanos()));
        result.put("runSeconds", seconds(runNanos));
        result.put("events", stats.events());
        result.put("loadedEvents", loaded.events().size());
        result.put("matches", config.enumerate ? stats.enumeratedMatches() : JSONObject.NULL);
        result.put("enumeratedMatches", stats.enumeratedMatches());
        result.put("triggers", stats.triggers());
        result.put("truncated", stats.truncated());
        result.put("truncatedTriggers", stats.truncatedTriggers());
        result.put("matchesComplete", config.enumerate && !stats.truncated() && !stats.timedOut());
        result.put("timedOut", stats.timedOut());
        result.put("inputLimited", loaded.limited());
        result.put("enumerationEnabled", config.enumerate);
        result.put("enumerationLimit", config.enumerationLimit);
        result.put("eventsPerSecond", stats.processNanos() == 0 ? 0 : stats.events() / seconds(stats.processNanos()));
        result.put("timing", "Inputs are preloaded. processSeconds excludes enumeration callbacks; enumerationSeconds includes detached match copying and output writes. runSeconds includes both. Final output flush is excluded.");
        return result;
      }
    } finally {
      if (output != null) output.close();
    }
  }

  private static double seconds(long nanos) {
    return nanos / 1e9;
  }

  private static final class Configuration {
    Path declarations;
    Path query;
    Path events;
    Path queryDescriptor;
    Path streamDescriptor;
    Path output;
    String stream = "S";
    CoreInputs.Format format = CoreInputs.Format.CSV;
    long maxEvents = -1;
    long enumerationLimit;
    double timeout;
    boolean enumerate = true;
    boolean help;
  }

  private record Inputs(Path declarations, Path query, List<CoreInputs.Source> sources, List<Path> descriptors) {}

  private static Configuration arguments(String[] args) {
    Configuration result = new Configuration();
    for (int i = 0; i < args.length; i++) {
      String option = args[i];
      switch (option) {
        case "--help", "-h" -> result.help = true;
        case "--declarations" -> result.declarations = Path.of(value(args, ++i, option));
        case "--query" -> result.query = Path.of(value(args, ++i, option));
        case "--events" -> result.events = Path.of(value(args, ++i, option));
        case "--stream" -> result.stream = value(args, ++i, option);
        case "--format" -> result.format = format(value(args, ++i, option));
        case "--output" -> result.output = Path.of(value(args, ++i, option));
        case "--max-events" -> result.maxEvents = nonnegativeLong(value(args, ++i, option), option);
        case "--enumeration-limit", "-i", "--limit" ->
            result.enumerationLimit = nonnegativeLong(value(args, ++i, option), option);
        case "--no-enumeration" -> result.enumerate = false;
        case "--timeout", "-t" -> result.timeout = nonnegativeDouble(value(args, ++i, option), option);
        case "-n", "--numevents" -> {
          long maximum = nonnegativeLong(value(args, ++i, option), option);
          result.maxEvents = maximum == 0 ? -1 : maximum;
        }
        case "-q", "--queryfile" -> result.queryDescriptor = Path.of(value(args, ++i, option));
        case "-s", "--streamsfile" -> result.streamDescriptor = Path.of(value(args, ++i, option));
        case "-e", "--exectime" -> booleanValue(value(args, ++i, option), option);
        case "-m", "--memtest" -> {
          if (booleanValue(value(args, ++i, option), option))
            throw new IllegalArgumentException("-m true is unsupported: this adapter measures preloaded offline execution, not the upstream GC memory experiment");
        }
        // The adapter always runs offline and never paces the input against the wall clock.
        case "-of", "-o", "--offline", "-f", "--fastrun" -> { }
        default -> throw new IllegalArgumentException("Unknown option: " + option + "; use --help");
      }
    }
    if (result.help) return result;
    boolean descriptors = result.queryDescriptor != null || result.streamDescriptor != null;
    if (descriptors) {
      if (result.queryDescriptor == null || result.streamDescriptor == null)
        throw new IllegalArgumentException("Original descriptor mode requires both -q and -s");
      if (result.declarations != null || result.query != null || result.events != null)
        throw new IllegalArgumentException("Use either -q/-s descriptors or --declarations/--query/--events");
    } else if (result.declarations == null || result.query == null || result.events == null) {
      throw new IllegalArgumentException("Required: --declarations FILE --query FILE --events FILE; use --help");
    }
    if (result.stream.isBlank()) throw new IllegalArgumentException("Stream name cannot be empty");
    if (!result.enumerate && result.output != null)
      throw new IllegalArgumentException("--output requires enumeration; remove --no-enumeration");
    return result;
  }

  private static Inputs inputs(Configuration config) throws IOException {
    if (config.queryDescriptor == null) {
      return new Inputs(config.declarations, config.query,
          List.of(new CoreInputs.Source(config.events, config.stream, config.format)), List.of());
    }
    List<String> queryLines = descriptorLines(config.queryDescriptor);
    if (queryLines.size() != 2)
      throw new IllegalArgumentException("Query descriptor requires two FILE:path lines (declarations, then queries)");
    Path declarations = descriptorFile(config.queryDescriptor, queryLines.get(0));
    Path query = descriptorFile(config.queryDescriptor, queryLines.get(1));
    List<CoreInputs.Source> sources = new ArrayList<>();
    for (String line : descriptorLines(config.streamDescriptor)) {
      String[] fields = line.split(":", 3);
      if (fields.length != 3 || fields[0].isBlank() || fields[2].isBlank())
        throw new IllegalArgumentException("Stream descriptor requires STREAM:FILE|CSV:path lines");
      CoreInputs.Format type = switch (fields[1].trim().toUpperCase(Locale.ROOT)) {
        case "FILE" -> CoreInputs.Format.EVENTS;
        case "CSV" -> CoreInputs.Format.CSV;
        default -> throw new IllegalArgumentException("Offline stream type must be FILE or CSV: " + fields[1]);
      };
      String stream = fields[0].trim();
      if (sources.stream().anyMatch(s -> s.stream().equals(stream)))
        throw new IllegalArgumentException("Duplicate source for stream " + stream);
      sources.add(new CoreInputs.Source(resolve(config.streamDescriptor, fields[2].trim()), stream, type));
    }
    if (sources.isEmpty()) throw new IllegalArgumentException("Stream descriptor contains no inputs");
    return new Inputs(declarations, query, List.copyOf(sources),
        List.of(config.queryDescriptor, config.streamDescriptor));
  }

  private static List<String> descriptorLines(Path path) throws IOException {
    return Files.readAllLines(path, StandardCharsets.UTF_8).stream().map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
  }

  private static Path descriptorFile(Path descriptor, String line) {
    String[] fields = line.split(":", 2);
    if (fields.length != 2 || !fields[0].trim().equalsIgnoreCase("FILE") || fields[1].isBlank())
      throw new IllegalArgumentException("Query descriptor only supports FILE:path: " + line);
    return resolve(descriptor, fields[1].trim());
  }

  private static Path resolve(Path descriptor, String text) {
    Path path = Path.of(text);
    if (path.isAbsolute() || Files.exists(path)) return path;
    Path parent = descriptor.toAbsolutePath().normalize().getParent();
    return parent.resolve(path).normalize();
  }

  private static void validateOutput(Path output, Inputs inputs) throws IOException {
    if (output == null) return;
    List<Path> inputPaths = new ArrayList<>(inputs.descriptors);
    inputPaths.add(inputs.declarations);
    inputPaths.add(inputs.query);
    inputs.sources.forEach(source -> inputPaths.add(source.path()));
    Path target = output.toAbsolutePath().normalize();
    for (Path input : inputPaths) {
      if (target.equals(input.toAbsolutePath().normalize())
          || (Files.exists(output) && Files.exists(input) && Files.isSameFile(output, input)))
        throw new IllegalArgumentException("Match output must differ from every input file: " + input);
    }
  }

  private static String value(String[] args, int index, String option) {
    if (index >= args.length || args[index].startsWith("--"))
      throw new IllegalArgumentException("Missing value for " + option);
    return args[index];
  }

  private static long nonnegativeLong(String value, String option) {
    try {
      long number = Long.parseLong(value);
      if (number < 0) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(option + " requires a nonnegative integer: " + value, error);
    }
  }

  private static double nonnegativeDouble(String value, String option) {
    try {
      double number = Double.parseDouble(value);
      if (!Double.isFinite(number) || number < 0) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(option + " requires a finite nonnegative number: " + value, error);
    }
  }

  private static boolean booleanValue(String value, String option) {
    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
      throw new IllegalArgumentException(option + " requires true or false: " + value);
    return Boolean.parseBoolean(value);
  }

  private static CoreInputs.Format format(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "csv" -> CoreInputs.Format.CSV;
      case "events" -> CoreInputs.Format.EVENTS;
      default -> throw new IllegalArgumentException("Format must be csv or events: " + value);
    };
  }

  private static String usage() {
    return """
        Usage: corecer.CoreMain --declarations FILE --query FILE --events FILE
               [--stream S] [--format csv|events] [--max-events N] [--timeout SECONDS]
               [--enumeration-limit N | --no-enumeration] [--output MATCHES.jsonl]
           or: corecer.CoreMain -q QUERY_DESCRIPTOR -s STREAM_DESCRIPTOR [options]

        CSV requires a header: event,time (or timestamp), followed by schema attributes.
        Comma or semicolon delimiters are detected. Time is milliseconds or
        yyyy-MM-dd HH:mm:ss in UTC. Each input's timestamps must be nondecreasing.
        Events format is E(attribute=value,...); absent time uses its zero-based row index.
        Events may supply __ts, timestamp, or time. Multiple descriptor streams are merged
        by timestamp, with ties following descriptor order then input order.

        All selected events are preloaded before execution, excluding input IO and parsing
        from processSeconds. Enumeration includes detached match copying and output writes.
        A zero enumeration limit means all matches; N limits each query trigger separately.
        --no-enumeration measures recognition only: matches is null, triggers remains valid.
        matches counts enumerated outputs; truncated means a callback stopped before exhaustion.
        Timeout covers processing and enumeration, starting after loading; checks are cooperative.
        --max-events 0 processes zero events; original -n 0 keeps its unlimited meaning.

        Original aliases: -i N, -n N, -t SECONDS, -e true|false, -m false.
        -of/-o/-f are accepted for this offline runner. -m true is explicitly unsupported.
        Query descriptor: FILE:declarations followed by FILE:queries.
        Stream descriptor: STREAM:FILE|CSV:path, one source per line.
        Relative descriptor paths use the working directory if present, then descriptor directory.
        Stdout is JSON metrics; --output writes JSON Lines of detached zero-based event indices.
        """;
  }
}
