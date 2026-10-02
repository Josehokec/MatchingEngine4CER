package opencep.condition;

public final class NotEqCondition extends BaseRelationCondition {
  public NotEqCondition(Object left, Object right) {
    super(left, right, RelopTypes.NOT_EQUAL);
  }
}
