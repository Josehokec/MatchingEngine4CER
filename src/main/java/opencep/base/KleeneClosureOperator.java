package opencep.base;

import java.util.*;

public final class KleeneClosureOperator extends UnaryStructure {
  public final int minSize;
  public final Integer maxSize;

  public KleeneClosureOperator(PatternStructure arg) {
    this(arg, 1, null);
  }

  public KleeneClosureOperator(PatternStructure arg, int minSize, Integer maxSize) {
    super(arg);
    if (arg instanceof NegationOperator || minSize < 1 || (maxSize != null && maxSize < minSize))
      throw new IllegalArgumentException("Invalid Kleene closure operand or bounds");
    this.minSize = minSize;
    this.maxSize = maxSize;
  }

  public PatternStructure getStructureProjection(Set<String> names) {
    var p = arg.getStructureProjection(names);
    return p == null ? null : new KleeneClosureOperator(p, minSize, maxSize);
  }

  public boolean equals(Object o) {
    return o instanceof KleeneClosureOperator k
        && arg.equals(k.arg)
        && minSize == k.minSize
        && Objects.equals(maxSize, k.maxSize);
  }

  public int hashCode() {
    return Objects.hash(arg, minSize, maxSize);
  }

  public String toString() {
    return "KL(" + arg + "," + minSize + "," + maxSize + ")";
  }
}
