package opencep.adaptive;

import java.util.*;

/** Immutable statistics indexed by primitive-event order in the pattern. */
public final class StatisticsSnapshot {
  private final double[] arrivalRates;
  private final double[][] selectivity;

  public StatisticsSnapshot(double[] rates, double[][] matrix) {
    arrivalRates = rates.clone();
    selectivity = new double[rates.length][rates.length];
    if (matrix != null && matrix.length != rates.length)
      throw new IllegalArgumentException("Statistics dimensions differ");
    for (int i = 0; i < rates.length; i++) {
      if (!Double.isFinite(rates[i]) || rates[i] < 0)
        throw new IllegalArgumentException("Invalid arrival rate");
      if (matrix != null && matrix[i].length != rates.length)
        throw new IllegalArgumentException("Statistics matrix is not square");
      for (int j = 0; j < rates.length; j++) {
        double value = matrix == null ? 1 : matrix[i][j];
        if (!Double.isFinite(value) || value < 0 || value > 1)
          throw new IllegalArgumentException("Invalid selectivity");
        selectivity[i][j] = value;
      }
    }
  }

  public static StatisticsSnapshot defaults(int size) {
    return new StatisticsSnapshot(new double[size], null);
  }

  public int size() {
    return arrivalRates.length;
  }

  public double rate(int i) {
    return arrivalRates[i];
  }

  public double selectivity(int i, int j) {
    return selectivity[i][j];
  }

  public double[] arrivalRates() {
    return arrivalRates.clone();
  }

  public double[][] selectivityMatrix() {
    return Arrays.stream(selectivity).map(double[]::clone).toArray(double[][]::new);
  }

  public StatisticsSnapshot project(List<String> oldNames, List<String> newNames) {
    double[] r = new double[newNames.size()];
    double[][] m = new double[r.length][r.length];
    for (int i = 0; i < r.length; i++) {
      int oi = oldNames.indexOf(newNames.get(i));
      r[i] = rate(oi);
      for (int j = 0; j < r.length; j++)
        m[i][j] = selectivity(oi, oldNames.indexOf(newNames.get(j)));
    }
    return new StatisticsSnapshot(r, m);
  }
}
