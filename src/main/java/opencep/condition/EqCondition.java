package opencep.condition;

public final class EqCondition extends BaseRelationCondition {
  public EqCondition(Object left, Object right) {
    super(left, right, RelopTypes.EQUAL);
  }
}
