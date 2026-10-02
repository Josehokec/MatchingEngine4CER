package opencep.tree;

public final class UnsortedPatternMatchStorage extends PatternMatchStorage {
  public UnsortedPatternMatchStorage(int cleanupInterval) {
    super(new TreeStorageParameters(false, cleanupInterval, true), null);
  }
}
