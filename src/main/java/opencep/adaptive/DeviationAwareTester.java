package opencep.adaptive;

public final class DeviationAwareTester {
  private final double threshold;

  public DeviationAwareTester(double threshold) {
    if (threshold < 0 || !Double.isFinite(threshold))
      throw new IllegalArgumentException("Invalid threshold");
    this.threshold = threshold;
  }

  private boolean changed(double next, double previous) {
    return next > previous * (1 + threshold) || next < previous * (1 - threshold);
  }

  public boolean isDeviated(StatisticsSnapshot next, StatisticsSnapshot previous) {
    if (previous == null || next.size() != previous.size()) return true;
    for (int i = 0; i < next.size(); i++) {
      if (changed(next.rate(i), previous.rate(i))) return true;
      for (int j = 0; j <= i; j++)
        if (changed(next.selectivity(i, j), previous.selectivity(i, j))) return true;
    }
    return false;
  }
}
