package opencep.tree;

/**
 * legacySemantics reproduces the historical Python evaluator, including its endpoint timestamps.
 */
public record TreeStorageParameters(
    boolean sortStorage,
    int cleanUpInterval,
    boolean prioritizeSortingByTimestamp,
    boolean legacySemantics) {
  public TreeStorageParameters() {
    this(false, 10, true, false);
  }

  public TreeStorageParameters(boolean sort, int cleanup, boolean timestamp) {
    this(sort, cleanup, timestamp, false);
  }

  public TreeStorageParameters {
    if (cleanUpInterval < 1)
      throw new IllegalArgumentException("Cleanup interval must be positive");
  }

  public TreeStorageParameters withLegacySemantics() {
    return new TreeStorageParameters(
        sortStorage, cleanUpInterval, prioritizeSortingByTimestamp, true);
  }
}
