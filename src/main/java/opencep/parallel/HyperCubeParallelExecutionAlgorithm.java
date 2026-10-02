package opencep.parallel;

import java.util.*;
import opencep.base.*;

public final class HyperCubeParallelExecutionAlgorithm implements DataParallelExecutionAlgorithm {
  private record Dimension(String attribute, int index) {}

  private final Map<String, List<Dimension>> dimensions = new HashMap<>();
  private final int[] shares;
  private final int units;

  public HyperCubeParallelExecutionAlgorithm(
      int requested, Map<String, List<String>> attributes, List<Pattern> patterns) {
    for (var pattern : patterns) {
      var types = pattern.getPrimitiveEvents().stream().map(PrimitiveEventStructure::type).toList();
      if (new HashSet<>(types).size() != types.size())
        throw new IllegalArgumentException(
            "Hypercube requires distinct event types in each pattern");
    }
    int count = 0;
    for (var entry : attributes.entrySet()) {
      var dims = new ArrayList<Dimension>();
      for (var attribute : entry.getValue()) dims.add(new Dimension(attribute, count++));
      dimensions.put(entry.getKey(), List.copyOf(dims));
    }
    if (count == 0) throw new IllegalArgumentException("Empty hypercube");
    shares = calcCubicShares(requested, count);
    units = Arrays.stream(shares).reduce(1, Math::multiplyExact);
  }

  public static int[] calcCubicShares(int units, int dimensions) {
    if (units < 1 || dimensions < 1) throw new IllegalArgumentException("Invalid cube size");
    var shares = new int[dimensions];
    Arrays.fill(shares, Math.max(1, (int) Math.floor(Math.pow(units, 1.0 / dimensions))));
    long product = 1;
    for (int share : shares) product *= share;
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int i = 0; i < shares.length; i++)
        if (product / shares[i] * (shares[i] + 1) <= units) {
          product = product / shares[i] * (shares[i] + 1);
          shares[i]++;
          changed = true;
        }
    }
    return shares;
  }

  public int units() {
    return units;
  }

  public Set<Integer> classify(Event event) {
    var dims = dimensions.get(event.type);
    var out = new HashSet<Integer>();
    if (dims == null) {
      for (int i = 0; i < units; i++) out.add(i);
      return out;
    }
    for (var dim : dims) {
      Object value = event.payload.get(dim.attribute);
      if (!(value instanceof Number n)) return Set.of();
      int wanted = Math.floorMod(n.longValue(), shares[dim.index]);
      int stride = 1;
      for (int i = dim.index + 1; i < shares.length; i++) stride *= shares[i];
      for (int unit = 0; unit < units; unit++)
        if (unit / stride % shares[dim.index] == wanted) out.add(unit);
    }
    return Set.copyOf(out);
  }

  public boolean isOwner(int unit, PatternMatch match) {
    Set<Integer> intersection = null;
    for (var event : match.primitiveEvents()) {
      var assigned = classify(event);
      if (intersection == null) intersection = new HashSet<>(assigned);
      else intersection.retainAll(assigned);
    }
    return intersection != null && !intersection.isEmpty() && unit == Collections.min(intersection);
  }
}
