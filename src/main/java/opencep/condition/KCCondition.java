package opencep.condition;

import java.util.*;
import java.util.function.*;
import opencep.base.*;

public abstract class KCCondition extends AtomicCondition {
  protected final Set<String> names;
  protected final Function<Map<String, Object>, Object> getter;
  protected final BiPredicate<Object, Object> relation;

  protected KCCondition(
      Set<String> names,
      Function<Map<String, Object>, Object> getter,
      BiPredicate<Object, Object> relation) {
    this.names = Set.copyOf(names);
    this.getter = getter;
    this.relation = relation;
  }

  public Result evaluate(Binding b) {
    return b.closureNames().containsAll(names) ? super.evaluate(b) : Result.UNKNOWN;
  }

  public Set<String> getEventNames() {
    return names;
  }

  protected List<Event> list(Binding b) {
    var sequence = b.closureEvents(names);
    return sequence != null ? sequence : names.stream().flatMap(n -> b.events(n).stream()).toList();
  }
}
