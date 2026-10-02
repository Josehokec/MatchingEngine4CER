package opencep.misc;

import java.time.Duration;
import java.util.*;
import opencep.adaptive.StatisticsSnapshot;
import opencep.base.*;
import opencep.condition.Condition;

/** Offline estimates for supplying a planner with statistics before a run. */
public final class LegacyStatistics {
  private LegacyStatistics() {}

  public static Map<String, Long> getOccurrences(Pattern pattern, List<Event> events) {
    Set<String> types = pattern.getAllEventTypes();
    Map<String, Long> counts = new HashMap<>();
    for (Event event : events)
      if (types.contains(event.type)) counts.merge(event.type, 1L, Long::sum);
    return Map.copyOf(counts);
  }

  public static double getConditionSelectivity(
      PrimitiveEventStructure a,
      PrimitiveEventStructure b,
      Condition condition,
      List<Event> events,
      boolean sequence) {
    if (condition == null) return 1;
    long total = 0, success = 0;
    for (Event left : events) {
      if (!left.type.equals(a.type())) continue;
      if (a.equals(b)) {
        total++;
        if (condition.eval(Binding.of(a.name(), left))) success++;
      } else {
        for (Event right : events) {
          if (!right.type.equals(b.type())
              || left.equals(right)
              || (sequence && left.timestamp.isAfter(right.timestamp))) continue;
          total++;
          Binding binding = Binding.of(a.name(), left).merge(Binding.of(b.name(), right), false);
          if (binding != null && condition.eval(binding)) success++;
        }
      }
    }
    return total == 0 ? 1 : success / (double) total;
  }

  public static StatisticsSnapshot estimate(Pattern pattern, List<Event> events) {
    var definitions = pattern.getPrimitiveEvents();
    var counts = getOccurrences(pattern, events);
    double seconds = 1;
    if (events.size() > 1)
      seconds =
          Math.max(
              1e-9,
              Duration.between(
                          events.stream()
                              .map(e -> e.timestamp)
                              .min(Comparator.naturalOrder())
                              .orElseThrow(),
                          events.stream()
                              .map(e -> e.timestamp)
                              .max(Comparator.naturalOrder())
                              .orElseThrow())
                      .toNanos()
                  / 1e9);
    double[] rates = new double[definitions.size()];
    double[][] matrix = new double[rates.length][rates.length];
    for (int i = 0; i < rates.length; i++) {
      rates[i] = counts.getOrDefault(definitions.get(i).type(), 0L) / seconds;
      for (int j = 0; j <= i; j++) {
        Set<String> names = new HashSet<>();
        names.add(definitions.get(i).name());
        names.add(definitions.get(j).name());
        Condition projected = pattern.condition.getConditionProjection(names);
        matrix[i][j] =
            matrix[j][i] =
                getConditionSelectivity(
                    definitions.get(i),
                    definitions.get(j),
                    projected,
                    events,
                    pattern.fullStructure instanceof SeqOperator);
      }
    }
    return new StatisticsSnapshot(rates, matrix);
  }
}
