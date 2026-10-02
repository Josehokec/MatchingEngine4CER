package opencep.base;

import java.util.*;

public final class NegationOperator extends UnaryStructure {
  public NegationOperator(PatternStructure arg) {
    super(arg);
  }

  public PatternStructure getStructureProjection(Set<String> names) {
    var p = arg.getStructureProjection(names);
    return p == null ? null : new NegationOperator(p);
  }

  public boolean equals(Object o) {
    return o instanceof NegationOperator n && arg.equals(n.arg);
  }

  public int hashCode() {
    return Objects.hash("NOT", arg);
  }

  public String toString() {
    return "NOT(" + arg + ")";
  }
}
