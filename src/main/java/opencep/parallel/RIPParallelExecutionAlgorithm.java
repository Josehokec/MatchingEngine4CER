package opencep.parallel;

import java.time.*;
import java.util.*;
import opencep.base.*;

public final class RIPParallelExecutionAlgorithm implements DataParallelExecutionAlgorithm {
  private final int units;
  private final Duration window;
  private final double interval;
  private Instant start;

  public RIPParallelExecutionAlgorithm(int units, Duration window, double multiple) {
    this.units = units;
    this.window = window;
    interval = Math.max(1, window.toNanos() * multiple);
  }

  public int units() {
    return units;
  }

  private int bucket(Instant time) {
    double delta = Duration.between(start, time).toNanos();
    return Math.floorMod((long) Math.floor(delta / interval), units);
  }

  public Set<Integer> classify(Event event) {
    if (start == null) start = event.timestamp;
    var out = new HashSet<Integer>();
    out.add(bucket(event.timestamp));
    out.add(bucket(event.timestamp.minus(window)));
    return Set.copyOf(out);
  }

  public boolean isOwner(int unit, PatternMatch match) {
    return bucket(match.firstTimestamp) == unit;
  }
}
