package opencep.condition;

public final class GreaterThanCondition extends BaseRelationCondition {
  public GreaterThanCondition(Object left, Object right) {
    super(left, right, RelopTypes.GREATER);
  }
}
