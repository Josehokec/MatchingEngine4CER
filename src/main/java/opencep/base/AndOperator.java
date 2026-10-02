package opencep.base;

public final class AndOperator extends CompositeStructure {
  public AndOperator(PatternStructure... args) {
    super(args);
  }

  protected CompositeStructure create(PatternStructure[] args) {
    return new AndOperator(args);
  }
}
