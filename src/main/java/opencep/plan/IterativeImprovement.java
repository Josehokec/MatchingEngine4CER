package opencep.plan;

import java.util.*;
import java.util.function.ToDoubleFunction;

public final class IterativeImprovement {
  private final Random random;

  public IterativeImprovement(long seed) {
    random = new Random(seed);
  }

  public List<Integer> execute(
      int limit, List<Integer> initial, boolean circles, ToDoubleFunction<List<Integer>> cost) {
    var current = new ArrayList<>(initial);
    double best = cost.applyAsDouble(current);
    for (int step = 0; step < limit && current.size() > 1; step++) {
      var next = new ArrayList<>(current);
      int i = random.nextInt(next.size()), j = random.nextInt(next.size());
      if (circles && next.size() >= 3) {
        int k;
        do {
          k = random.nextInt(next.size());
        } while (k == i || k == j);
        if (i == j) continue;
        int old = next.get(i);
        next.set(i, next.get(j));
        next.set(j, next.get(k));
        next.set(k, old);
      } else Collections.swap(next, i, j);
      double c = cost.applyAsDouble(next);
      if (c < best) {
        current = next;
        best = c;
      }
    }
    return List.copyOf(current);
  }
}
