package opencep.tree;

import java.util.function.Function;

public final class SortedPatternMatchStorage extends PatternMatchStorage {
  public SortedPatternMatchStorage(Function<Candidate, Object> key, int cleanupInterval) {
    super(new TreeStorageParameters(true, cleanupInterval, true), key);
  }
}
