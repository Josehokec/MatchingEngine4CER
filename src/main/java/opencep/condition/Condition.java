package opencep.condition;

import java.util.*;
import opencep.base.Binding;

/** Partial evaluation uses three-valued logic so OR cannot reject a missing branch. */
public interface Condition {
  enum Result {
    TRUE,
    FALSE,
    UNKNOWN
  }

  Result evaluate(Binding binding);

  Set<String> getEventNames();

  default boolean eval(Binding binding) {
    return evaluate(binding) == Result.TRUE;
  }

  default boolean mayMatch(Binding binding) {
    return evaluate(binding) != Result.FALSE;
  }

  default List<AtomicCondition> extractAtomicConditions() {
    return this instanceof AtomicCondition a ? List.of(a) : List.of();
  }

  default Condition getConditionProjection(Set<String> names) {
    return names.containsAll(getEventNames()) ? this : null;
  }

  default Condition getConditionsIntersection(Condition other) {
    var atoms =
        extractAtomicConditions().stream()
            .filter(other.extractAtomicConditions()::contains)
            .toArray(Condition[]::new);
    return atoms.length == 0 ? null : new AndCondition(atoms);
  }
}
