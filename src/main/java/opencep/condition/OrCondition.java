package opencep.condition;

public final class OrCondition extends CompositeCondition {
  public OrCondition(Condition... cs) {
    super(cs);
  }

  protected boolean conjunction() {
    return false;
  }

  protected CompositeCondition create(Condition[] cs) {
    return new OrCondition(cs);
  }
}
