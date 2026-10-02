package opencep.misc;

import java.util.*;

/** Row-major generic array used by hypercube routing and array utility callers. */
public final class NDArray<T> implements Iterable<T> {
  private final List<T> values;
  private final int[] shape;

  public NDArray(List<T> values, int... shape) {
    this.values = List.copyOf(values);
    this.shape = shape.length == 0 ? new int[] {values.size()} : shape.clone();
    int size = 1;
    for (int s : this.shape) {
      if (s < 0) throw new IllegalArgumentException("Negative dimension");
      size = Math.multiplyExact(size, s);
    }
    if (size != values.size()) throw new IllegalArgumentException("Shape does not match data size");
  }

  public static NDArray<Object> fromNested(List<?> nested) {
    var shape = dimensions(nested);
    var flat = new ArrayList<Object>();
    flatten(nested, flat);
    return new NDArray<>(flat, shape.stream().mapToInt(Integer::intValue).toArray());
  }

  private static List<Integer> dimensions(Object item) {
    if (!(item instanceof List<?> values)) return List.of();
    List<Integer> child = values.isEmpty() ? List.of() : dimensions(values.get(0));
    for (var value : values)
      if (!dimensions(value).equals(child))
        throw new IllegalArgumentException("Ragged nested array");
    var dimensions = new ArrayList<Integer>();
    dimensions.add(values.size());
    dimensions.addAll(child);
    return dimensions;
  }

  private static void flatten(Object item, List<Object> flat) {
    if (item instanceof List<?> values) values.forEach(value -> flatten(value, flat));
    else flat.add(item);
  }

  public int size() {
    return values.size();
  }

  public int[] shape() {
    return shape.clone();
  }

  public int ndim() {
    return shape.length;
  }

  public NDArray<T> reshape(int... target) {
    int unknown = -1, known = 1;
    var actual = target.clone();
    for (int i = 0; i < actual.length; i++)
      if (actual[i] == -1) {
        if (unknown != -1) throw new IllegalArgumentException("Multiple inferred dimensions");
        unknown = i;
      } else known = Math.multiplyExact(known, actual[i]);
    if (unknown != -1) {
      if (known == 0 || size() % known != 0)
        throw new IllegalArgumentException("Cannot infer shape");
      actual[unknown] = size() / known;
    }
    return new NDArray<>(values, actual);
  }

  public T get(int... indices) {
    if (indices.length != shape.length)
      throw new IllegalArgumentException("Wrong number of indices");
    int index = 0;
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] < 0 || indices[i] >= shape[i]) throw new IndexOutOfBoundsException();
      index = index * shape[i] + indices[i];
    }
    return values.get(index);
  }

  public record Slice(Integer start, Integer stop, int step) {
    public Slice() {
      this(null, null, 1);
    }

    public Slice {
      if (step == 0) throw new IllegalArgumentException("A slice step cannot be zero");
    }
  }

  /**
   * Integer selectors remove an axis; Slice selectors preserve an axis. Omitted axes use a full
   * slice.
   */
  public Object slice(Object... selectors) {
    if (selectors.length > shape.length) throw new IllegalArgumentException("Too many selectors");
    var indices = new ArrayList<List<Integer>>();
    var retained = new ArrayList<Integer>();
    for (int axis = 0; axis < shape.length; axis++) {
      Object selector = axis < selectors.length ? selectors[axis] : new Slice();
      int size = shape[axis];
      if (selector instanceof Integer index) {
        int normalized = index < 0 ? size + index : index;
        if (normalized < 0 || normalized >= size) throw new IndexOutOfBoundsException();
        indices.add(List.of(normalized));
      } else if (selector instanceof Slice range) {
        int step = range.step();
        int start =
            range.start() == null
                ? (step > 0 ? 0 : size - 1)
                : normalizeSlice(range.start(), size, step);
        int stop =
            range.stop() == null
                ? (step > 0 ? size : -1)
                : normalizeSlice(range.stop(), size, step);
        var positions = new ArrayList<Integer>();
        for (int i = start; step > 0 ? i < stop : i > stop; i += step) positions.add(i);
        indices.add(positions);
        retained.add(positions.size());
      } else throw new IllegalArgumentException("Use Integer or Slice selectors");
    }
    var out = new ArrayList<T>();
    sliceRecursive(indices, 0, 0, out);
    if (retained.isEmpty()) return out.get(0);
    return new NDArray<>(out, retained.stream().mapToInt(Integer::intValue).toArray());
  }

  private int normalizeSlice(int index, int size, int step) {
    int i = index < 0 ? size + index : index;
    return step > 0 ? Math.max(0, Math.min(size, i)) : Math.max(-1, Math.min(size - 1, i));
  }

  private void sliceRecursive(List<List<Integer>> indices, int axis, int offset, List<T> out) {
    if (axis == shape.length) {
      out.add(values.get(offset));
      return;
    }
    for (int index : indices.get(axis))
      sliceRecursive(indices, axis + 1, offset * shape[axis] + index, out);
  }

  public List<T> toList() {
    return values;
  }

  public Iterator<T> iterator() {
    return values.iterator();
  }
}
