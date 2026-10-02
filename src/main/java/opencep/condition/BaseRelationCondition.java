package opencep.condition;

import java.math.BigDecimal;
import java.util.*;

public class BaseRelationCondition extends BinaryCondition {
  public final RelopTypes relopType;

  public BaseRelationCondition(Object left, Object right, RelopTypes op) {
    super(left, right, (a, b) -> compare(a, b, op));
    if (!(left instanceof Variable) && !(right instanceof Variable))
      throw new IllegalArgumentException("A relation needs a variable");
    relopType = op;
  }

  @SuppressWarnings({"unchecked"})
  public static int compareValues(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y)
      return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()));
    if (a instanceof String x && b instanceof String y) return x.compareTo(y);
    if (Objects.equals(a, b)) return 0;
    if (a != null && b != null && a.getClass().isInstance(b) && a instanceof Comparable comparable)
      return comparable.compareTo(b);
    throw new IllegalArgumentException("Values are not comparable: " + a + ", " + b);
  }

  private static boolean compare(Object a, Object b, RelopTypes op) {
    if (op == RelopTypes.EQUAL || op == RelopTypes.NOT_EQUAL) {
      boolean same =
          a instanceof Number && b instanceof Number
              ? compareValues(a, b) == 0
              : Objects.equals(a, b);
      return op == RelopTypes.EQUAL ? same : !same;
    }
    return op.test(compareValues(a, b));
  }

  public boolean equals(Object o) {
    if (!(o instanceof BaseRelationCondition r)) return false;
    return (relopType == r.relopType
            && Objects.equals(leftTerm, r.leftTerm)
            && Objects.equals(rightTerm, r.rightTerm))
        || (relopType.opposite() == r.relopType
            && Objects.equals(leftTerm, r.rightTerm)
            && Objects.equals(rightTerm, r.leftTerm));
  }

  public int hashCode() {
    return Objects.hashCode(leftTerm) + Objects.hashCode(rightTerm);
  }

  public String toString() {
    return leftTerm + " " + relopType + " " + rightTerm;
  }
}
