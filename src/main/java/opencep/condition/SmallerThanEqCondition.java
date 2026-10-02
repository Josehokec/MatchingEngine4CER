package opencep.condition;

public final class SmallerThanEqCondition extends BaseRelationCondition {
  public SmallerThanEqCondition(Object left, Object right) {
    super(left, right, RelopTypes.SMALLER_EQUAL);
  }
}
