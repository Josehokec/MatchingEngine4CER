package opencep.condition;

import java.util.*;
import opencep.base.Binding;

public abstract class CompositeCondition implements Condition {
  public final List<Condition> conditions;

  protected CompositeCondition(Condition... conditions) {
    this.conditions = List.of(conditions);
  }

  protected abstract boolean conjunction();

  protected abstract CompositeCondition create(Condition[] cs);

  public Result evaluate(Binding b) {
    boolean unknown = false;
    for (var c : conditions) {
      var r = c.evaluate(b);
      if (r == Result.UNKNOWN) unknown = true;
      else if (conjunction() && r == Result.FALSE) return Result.FALSE;
      else if (!conjunction() && r == Result.TRUE) return Result.TRUE;
    }
    if (unknown) return Result.UNKNOWN;
    return conjunction() ? Result.TRUE : Result.FALSE;
  }

  public Set<String> getEventNames() {
    var names = new HashSet<String>();
    conditions.forEach(c -> names.addAll(c.getEventNames()));
    return Set.copyOf(names);
  }

  public List<AtomicCondition> extractAtomicConditions() {
    return conditions.stream().flatMap(c -> c.extractAtomicConditions().stream()).toList();
  }

  public Condition getConditionProjection(Set<String> names) {
    var cs =
        conditions.stream()
            .map(c -> c.getConditionProjection(names))
            .filter(Objects::nonNull)
            .toArray(Condition[]::new);
    return cs.length == 0 ? null : create(cs);
  }

  public Condition getConditionOf(Set<String> names) {
    return getConditionProjection(names);
  }

  public List<Condition> getConditionsList() {
    return conditions;
  }

  public boolean equals(Object o) {
    return o != null
        && getClass() == o.getClass()
        && conditions.equals(((CompositeCondition) o).conditions);
  }

  public int hashCode() {
    return Objects.hash(getClass(), conditions);
  }

  public String toString() {
    return (conjunction() ? "AND" : "OR") + conditions;
  }
}
