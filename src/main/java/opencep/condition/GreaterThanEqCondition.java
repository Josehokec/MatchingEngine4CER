package opencep.condition;

public final class GreaterThanEqCondition extends BaseRelationCondition {
  public GreaterThanEqCondition(Object left, Object right) {
    super(left, right, RelopTypes.GREATER_EQUAL);
  }
}
