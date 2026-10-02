package opencep.misc;

import java.util.*;
import java.util.function.*;

public final class Utils {
  private Utils() {}

  public static Object strToNumber(String text) {
    var value = text.strip();
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ignored) {
      return value;
    }
  }

  public static Double calculateJointProbability(Double a, Double b) {
    if (a == null) return b;
    if (b == null) return a;
    return a * b;
  }

  public static <T> int lowerBound(List<T> values, T target, Comparator<T> cmp) {
    int lo = 0, hi = values.size();
    while (lo < hi) {
      int m = (lo + hi) >>> 1;
      if (cmp.compare(values.get(m), target) < 0) lo = m + 1;
      else hi = m;
    }
    return lo;
  }

  public static <T> int upperBound(List<T> values, T target, Comparator<T> cmp) {
    int lo = 0, hi = values.size();
    while (lo < hi) {
      int m = (lo + hi) >>> 1;
      if (cmp.compare(values.get(m), target) <= 0) lo = m + 1;
      else hi = m;
    }
    return lo;
  }

  public static <T> List<T> merge(List<T> a, List<T> b, Comparator<T> cmp) {
    var out = new ArrayList<T>();
    int i = 0, j = 0;
    while (i < a.size() && j < b.size())
      out.add(cmp.compare(a.get(i), b.get(j)) < 0 ? a.get(i++) : b.get(j++));
    out.addAll(a.subList(i, a.size()));
    out.addAll(b.subList(j, b.size()));
    return out;
  }

  public static <T> boolean isSorted(List<T> values, Comparator<T> cmp) {
    for (int i = 1; i < values.size(); i++)
      if (cmp.compare(values.get(i - 1), values.get(i)) > 0) return false;
    return true;
  }

  public static <T> void powerset(List<T> values, int min, int max, Consumer<List<T>> visitor) {
    combinations(values, 0, new ArrayList<>(), min, max, visitor);
  }

  private static <T> void combinations(
      List<T> values, int offset, List<T> chosen, int min, int max, Consumer<List<T>> visitor) {
    if (chosen.size() >= min) visitor.accept(List.copyOf(chosen));
    if (chosen.size() == max) return;
    for (int i = offset; i < values.size(); i++) {
      chosen.add(values.get(i));
      combinations(values, i + 1, chosen, min, max, visitor);
      chosen.remove(chosen.size() - 1);
    }
  }
}
