package opencep.base;

import java.util.*;

public abstract class UnaryStructure implements PatternStructure {
  public final PatternStructure arg;

  protected UnaryStructure(PatternStructure arg) {
    this.arg = Objects.requireNonNull(arg);
  }

  public List<String> getAllEventNames() {
    return arg.getAllEventNames();
  }

  public List<PrimitiveEventStructure> getPrimitiveEvents() {
    return arg.getPrimitiveEvents();
  }
}
