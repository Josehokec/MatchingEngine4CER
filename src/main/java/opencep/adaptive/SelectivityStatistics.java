package opencep.adaptive;

import java.util.*;
import opencep.base.Pattern;
import opencep.condition.AtomicCondition;

public final class SelectivityStatistics {
  private final Pattern pattern;
  private final Map<AtomicCondition, long[]> counts = new IdentityHashMap<>();

  public SelectivityStatistics(Pattern pattern) {
    this.pattern = pattern;
    pattern.condition.extractAtomicConditions().forEach(c -> counts.put(c, new long[2]));
  }

  public void update(AtomicCondition condition, boolean success) {
    var count = counts.get(condition);
    if (count != null) {
      count[0]++;
      if (success) count[1]++;
    }
  }

  public double[][] getStatistics() {
    int size = pattern.countPrimitiveEvents();
    double[][] matrix =
        pattern.getStatistics() == null
            ? new StatisticsSnapshot(new double[size], null).selectivityMatrix()
            : pattern.getStatistics().selectivityMatrix();
    for (int i = 0; i < size; i++)
      for (int j = 0; j <= i; j++) {
        var names = new HashSet<String>();
        names.add(pattern.getPrimitiveEvents().get(i).name());
        names.add(pattern.getPrimitiveEvents().get(j).name());
        double value = 1;
        boolean relevant = false;
        for (var entry : counts.entrySet())
          if (names.containsAll(entry.getKey().getEventNames())) {
            relevant = true;
            var c = entry.getValue();
            if (c[0] > 0) value *= c[1] / (double) c[0];
          }
        if (relevant) matrix[i][j] = matrix[j][i] = value;
      }
    return matrix;
  }
}
