package cersrt;

import java.util.Objects;

/** Window zero is unbounded; otherwise the span must be strictly smaller than the window. */
public record Query(
    Expression expression, long window, WindowType windowType, String partitionBy, int order) {
  public enum WindowType {
    COUNT,
    TIME
  }

  public Query {
    Objects.requireNonNull(expression, "expression");
    Objects.requireNonNull(windowType, "windowType");
    Objects.requireNonNull(partitionBy, "partitionBy");
    if (window < 0 || order < 0) throw new IllegalArgumentException("Negative window or order");
    if (partitionBy.isBlank()) throw new IllegalArgumentException("Empty partition attribute");
  }

  public Query(Expression expression, long window) {
    this(expression, window, WindowType.TIME, "$", 0);
  }

  public Query withWindow(long size) {
    return new Query(expression, size, windowType, partitionBy, order);
  }
}
