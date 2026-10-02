package opencep.base;

import java.util.*;

/** Immutable pattern syntax tree. Event aliases identify variable bindings. */
public interface PatternStructure {
  List<String> getAllEventNames();

  List<PrimitiveEventStructure> getPrimitiveEvents();

  PatternStructure getStructureProjection(Set<String> names);

  default boolean containsEvent(String name) {
    return getAllEventNames().contains(name);
  }

  default PatternStructure duplicate() {
    return this;
  }

  default Class<?> getTopOperator() {
    return getClass();
  }
}
