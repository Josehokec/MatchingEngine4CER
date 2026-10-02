package opencep.parallel;

import java.util.*;

public record ParallelExecutionParameters(
    ParallelExecutionModes mode,
    int units,
    DataParallelExecutionModes algorithm,
    String key,
    Map<String, List<String>> attributes,
    double multiple) {
  public ParallelExecutionParameters() {
    this(
        ParallelExecutionModes.SEQUENTIAL,
        1,
        DataParallelExecutionModes.RIP_ALGORITHM,
        null,
        Map.of(),
        12);
  }

  public ParallelExecutionParameters {
    if (mode == null
        || algorithm == null
        || units < 1
        || multiple < 1
        || !Double.isFinite(multiple))
      throw new IllegalArgumentException("Invalid parallel parameters");
    var copy = new LinkedHashMap<String, List<String>>();
    attributes.forEach((k, v) -> copy.put(k, List.copyOf(v)));
    attributes = Collections.unmodifiableMap(copy);
    if (mode == ParallelExecutionModes.DATA_PARALLEL
        && algorithm == DataParallelExecutionModes.GROUP_BY_KEY_ALGORITHM
        && (key == null || key.isEmpty()))
      throw new IllegalArgumentException("Group-by-key needs an attribute");
    if (mode == ParallelExecutionModes.DATA_PARALLEL
        && algorithm == DataParallelExecutionModes.HYPER_CUBE_ALGORITHM
        && attributes.isEmpty()) throw new IllegalArgumentException("Hypercube needs dimensions");
  }

  public static ParallelExecutionParameters rip(int units) {
    return new ParallelExecutionParameters(
        ParallelExecutionModes.DATA_PARALLEL,
        units,
        DataParallelExecutionModes.RIP_ALGORITHM,
        null,
        Map.of(),
        12);
  }

  public static ParallelExecutionParameters groupByKey(int units, String key) {
    return new ParallelExecutionParameters(
        ParallelExecutionModes.DATA_PARALLEL,
        units,
        DataParallelExecutionModes.GROUP_BY_KEY_ALGORITHM,
        key,
        Map.of(),
        12);
  }

  public static ParallelExecutionParameters hyperCube(
      int units, Map<String, List<String>> attributes) {
    return new ParallelExecutionParameters(
        ParallelExecutionModes.DATA_PARALLEL,
        units,
        DataParallelExecutionModes.HYPER_CUBE_ALGORITHM,
        null,
        attributes,
        12);
  }
}
