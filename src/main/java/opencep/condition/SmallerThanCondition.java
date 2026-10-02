package opencep.condition;

public final class SmallerThanCondition extends BaseRelationCondition {
  public SmallerThanCondition(Object left, Object right) {
    super(left, right, RelopTypes.SMALLER);
  }
}
