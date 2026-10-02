package opencep.parallel;

import java.util.*;
import opencep.base.*;

public final class GroupByKeyParallelExecutionAlgorithm implements DataParallelExecutionAlgorithm {
  private final int units;
  private final String key;

  public GroupByKeyParallelExecutionAlgorithm(int units, String key) {
    this.units = units;
    this.key = key;
  }

  public int units() {
    return units;
  }

  public Set<Integer> classify(Event event) {
    Object value = event.payload.get(key);
    return value instanceof Number n ? Set.of(Math.floorMod(n.longValue(), units)) : Set.of();
  }

  public boolean isOwner(int unit, PatternMatch match) {
    return true;
  }
}
