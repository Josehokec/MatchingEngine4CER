package opencep.base;

public final class SeqOperator extends CompositeStructure {
  public SeqOperator(PatternStructure... args) {
    super(args);
  }

  protected CompositeStructure create(PatternStructure[] args) {
    return new SeqOperator(args);
  }
}
