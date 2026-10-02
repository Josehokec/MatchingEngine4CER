package opencep;

import java.util.*;
import opencep.base.*;
import opencep.evaluation.*;
import opencep.parallel.*;
import opencep.stream.OutputStream;
import opencep.transformation.*;

/** Entry point for evaluating one or more patterns on an ordered event stream. */
public final class CEP {
  private final EvaluationManager manager;
  private OutputStream<PatternMatch> output;
  private boolean running;

  public CEP(Pattern pattern) {
    this(List.of(pattern));
  }

  public CEP(List<Pattern> patterns) {
    this(
        patterns,
        new EvaluationMechanismParameters(),
        new ParallelExecutionParameters(),
        new PatternPreprocessingParameters());
  }

  public CEP(
      List<Pattern> patterns,
      EvaluationMechanismParameters evaluation,
      ParallelExecutionParameters parallel,
      PatternPreprocessingParameters preprocessing) {
    if (patterns == null || patterns.isEmpty())
      throw new IllegalArgumentException("At least one pattern is required");
    var processed = new PatternPreprocessor(preprocessing).transformPatterns(patterns);
    // Upstream numbers an unnamed workload when multiple patterns are evaluated.
    if (patterns.size() > 1) {
      var identified = new ArrayList<Pattern>();
      for (int i = 0; i < processed.size(); i++) {
        var p = processed.get(i);
        if (p.id == null) {
          var copy =
              new Pattern(
                  p.fullStructure, p.condition, p.window, p.consumptionPolicy, i + 1, p.confidence);
          copy.setStatistics(p.getStatistics());
          identified.add(copy);
        } else identified.add(p);
      }
      processed = List.copyOf(identified);
    }
    manager =
        EvaluationManagerFactory.createEvaluationManager(
            processed,
            evaluation == null ? new EvaluationMechanismParameters() : evaluation,
            parallel == null ? new ParallelExecutionParameters() : parallel);
  }

  public double run(
      Iterable<String> rawEvents, OutputStream<PatternMatch> matches, DataFormatter formatter) {
    Objects.requireNonNull(formatter);
    Iterable<Event> parsed =
        () ->
            new Iterator<>() {
              final Iterator<String> raw = rawEvents.iterator();

              public boolean hasNext() {
                return raw.hasNext();
              }

              public Event next() {
                return new Event(raw.next(), formatter);
              }
            };
    return runEvents(parsed, matches);
  }

  public double runEvents(Iterable<Event> events, OutputStream<PatternMatch> matches) {
    Objects.requireNonNull(events);
    Objects.requireNonNull(matches);
    synchronized (this) {
      if (running) throw new IllegalStateException("CEP is already running");
      running = true;
      output = matches;
    }
    long start = System.nanoTime();
    try {
      manager.eval(events, matches);
      return (System.nanoTime() - start) / 1e9;
    } finally {
      synchronized (this) {
        running = false;
      }
    }
  }

  public PatternMatch getPatternMatch() {
    if (output == null) return null;
    return output.getItem();
  }

  public OutputStream<PatternMatch> getPatternMatchStream() {
    return output;
  }

  public String getEvaluationMechanismStructureSummary() {
    return manager.getStructureSummary();
  }
}
