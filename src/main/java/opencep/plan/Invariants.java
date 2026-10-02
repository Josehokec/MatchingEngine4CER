package opencep.plan;

import java.util.*;
import opencep.adaptive.StatisticsSnapshot;

/** Saved comparisons that justified a plan choice. */
public final class Invariants {
  @FunctionalInterface
  public interface Check {
    boolean violated(StatisticsSnapshot statistics);
  }

  private final List<Check> checks = new ArrayList<>();

  public void add(Check check) {
    checks.add(check);
  }

  public boolean isInvariantsViolated(StatisticsSnapshot stats) {
    return checks.stream().anyMatch(c -> c.violated(stats));
  }

  public int size() {
    return checks.size();
  }
}
