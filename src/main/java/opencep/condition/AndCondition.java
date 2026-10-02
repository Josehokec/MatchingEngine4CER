package opencep.condition;

public final class AndCondition extends CompositeCondition {
  public AndCondition(Condition... cs) {
    super(cs);
  }

  protected boolean conjunction() {
    return true;
  }

  protected CompositeCondition create(Condition[] cs) {
    return new AndCondition(cs);
  }
}
