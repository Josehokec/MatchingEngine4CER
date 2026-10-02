package opencep.condition;

import java.util.*;
import java.util.function.Predicate;
import opencep.base.*;

public class SimpleCondition extends AtomicCondition {
  public final List<Object> terms;
  private final Predicate<List<Object>> relation;

  public SimpleCondition(Predicate<List<Object>> relation, Object... terms) {
    this.terms = Collections.unmodifiableList(Arrays.asList(terms.clone()));
    this.relation = Objects.requireNonNull(relation);
  }

  public Set<String> getEventNames() {
    var out = new LinkedHashSet<String>();
    for (var t : terms) if (t instanceof Variable v) out.add(v.name());
    return Set.copyOf(out);
  }

  protected boolean test(Binding b) {
    return expand(b, 0, new ArrayList<>());
  }

  private boolean expand(Binding b, int i, List<Object> values) {
    if (i == terms.size()) return relation.test(Collections.unmodifiableList(values));
    Object term = terms.get(i);
    List<Object> options =
        term instanceof Variable v
            ? b.events(v.name()).stream().map(e -> v.getter().apply(e.payload)).toList()
            : Collections.singletonList(term);
    for (Object x : options) {
      values.add(x);
      boolean ok = expand(b, i + 1, values);
      values.remove(values.size() - 1);
      if (!ok) return false;
    }
    return true;
  }

  public String toString() {
    return "CONDITION" + terms;
  }
}
