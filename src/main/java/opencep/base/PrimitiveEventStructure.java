package opencep.base;

import java.util.*;

public record PrimitiveEventStructure(String type, String name) implements PatternStructure {
  public PrimitiveEventStructure {
    Objects.requireNonNull(type);
    Objects.requireNonNull(name);
  }

  public List<String> getAllEventNames() {
    return List.of(name);
  }

  public List<PrimitiveEventStructure> getPrimitiveEvents() {
    return List.of(this);
  }

  public PatternStructure getStructureProjection(Set<String> names) {
    return names.contains(name) ? this : null;
  }

  public String toString() {
    return type + " " + name;
  }
}
