package opencep.condition;

public enum RelopTypes {
  EQUAL,
  NOT_EQUAL,
  GREATER,
  GREATER_EQUAL,
  SMALLER,
  SMALLER_EQUAL;

  public RelopTypes opposite() {
    return switch (this) {
      case GREATER -> SMALLER;
      case SMALLER -> GREATER;
      case GREATER_EQUAL -> SMALLER_EQUAL;
      case SMALLER_EQUAL -> GREATER_EQUAL;
      default -> this;
    };
  }

  public boolean test(int cmp) {
    return switch (this) {
      case EQUAL -> cmp == 0;
      case NOT_EQUAL -> cmp != 0;
      case GREATER -> cmp > 0;
      case GREATER_EQUAL -> cmp >= 0;
      case SMALLER -> cmp < 0;
      case SMALLER_EQUAL -> cmp <= 0;
    };
  }
}
