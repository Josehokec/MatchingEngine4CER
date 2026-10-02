package opencep.condition;

import java.util.*;
import java.util.function.*;
import opencep.base.*;

public final class KCIndexCondition extends KCCondition {
  public final Integer firstIndex, secondIndex, offset;

  public KCIndexCondition(
      Set<String> names,
      Function<Map<String, Object>, Object> getter,
      BiPredicate<Object, Object> relation,
      int offset) {
    this(names, getter, relation, null, null, offset);
  }

  public KCIndexCondition(
      Set<String> names,
      Function<Map<String, Object>, Object> getter,
      BiPredicate<Object, Object> relation,
      Integer first,
      Integer second,
      Integer offset) {
    super(names, getter, relation);
    if (!((offset != null && first == null && second == null)
        || (offset == null && first != null && second != null)))
      throw new IllegalArgumentException("Specify two indices or one offset");
    firstIndex = first;
    secondIndex = second;
    this.offset = offset;
  }

  protected boolean test(Binding b) {
    var es = list(b);
    if (offset != null) {
      if (offset >= es.size()) return false;
      for (int i = 0; i < es.size(); i++) {
        int j = i + offset;
        if (j >= 0
            && j < es.size()
            && !relation.test(getter.apply(es.get(i).payload), getter.apply(es.get(j).payload)))
          return false;
      }
      return true;
    }
    return firstIndex >= 0
        && secondIndex >= 0
        && firstIndex < es.size()
        && secondIndex < es.size()
        && relation.test(
            getter.apply(es.get(firstIndex).payload), getter.apply(es.get(secondIndex).payload));
  }
}
