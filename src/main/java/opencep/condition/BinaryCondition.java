package opencep.condition;

import java.util.function.BiPredicate;

public class BinaryCondition extends SimpleCondition {
  public final Object leftTerm, rightTerm;

  public BinaryCondition(Object left, Object right, BiPredicate<Object, Object> relation) {
    super(v -> relation.test(v.get(0), v.get(1)), left, right);
    leftTerm = left;
    rightTerm = right;
  }
}
