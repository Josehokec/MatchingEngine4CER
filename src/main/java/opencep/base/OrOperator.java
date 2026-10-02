package opencep.base;

public final class OrOperator extends CompositeStructure {
  public OrOperator(PatternStructure... args) {
    super(args);
  }

  protected CompositeStructure create(PatternStructure[] args) {
    return new OrOperator(args);
  }
}
