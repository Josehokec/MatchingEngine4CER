package opencep.condition;

import java.util.*;
import java.util.function.*;
import opencep.base.*;

public final class KCValueCondition extends KCCondition {
  public final Object value;
  public final Integer index;

  public KCValueCondition(
      Set<String> names,
      Function<Map<String, Object>, Object> getter,
      BiPredicate<Object, Object> relation,
      Object value) {
    this(names, getter, relation, value, null);
  }

  public KCValueCondition(
      Set<String> names,
      Function<Map<String, Object>, Object> getter,
      BiPredicate<Object, Object> relation,
      Object value,
      Integer index) {
    super(names, getter, relation);
    this.value = value;
    this.index = index;
  }

  protected boolean test(Binding b) {
    var es = list(b);
    if (index != null)
      return index >= 0
          && index < es.size()
          && relation.test(getter.apply(es.get(index).payload), value);
    return es.stream().allMatch(e -> relation.test(getter.apply(e.payload), value));
  }
}
