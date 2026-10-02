package opencep.condition;

import java.util.*;
import opencep.base.Binding;

public final class TrueCondition extends AtomicCondition {
  protected boolean test(Binding b) {
    return true;
  }

  public Set<String> getEventNames() {
    return Set.of();
  }

  public List<AtomicCondition> extractAtomicConditions() {
    return List.of();
  }

  public boolean equals(Object o) {
    return o instanceof TrueCondition;
  }

  public int hashCode() {
    return 1;
  }

  public String toString() {
    return "TRUE";
  }
}
