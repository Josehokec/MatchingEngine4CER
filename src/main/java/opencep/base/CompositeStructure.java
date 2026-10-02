package opencep.base;

import java.util.*;

public abstract class CompositeStructure implements PatternStructure {
  public final List<PatternStructure> args;

  protected CompositeStructure(PatternStructure... args) {
    this.args = List.of(args);
    if (this.args.isEmpty())
      throw new IllegalArgumentException("An operator needs at least one operand");
  }

  protected abstract CompositeStructure create(PatternStructure[] args);

  public List<String> getAllEventNames() {
    return args.stream().flatMap(a -> a.getAllEventNames().stream()).toList();
  }

  public List<PrimitiveEventStructure> getPrimitiveEvents() {
    return args.stream().flatMap(a -> a.getPrimitiveEvents().stream()).distinct().toList();
  }

  public PatternStructure getStructureProjection(Set<String> names) {
    PatternStructure[] projected =
        args.stream()
            .map(a -> a.getStructureProjection(names))
            .filter(Objects::nonNull)
            .toArray(PatternStructure[]::new);
    return projected.length == 0 ? null : create(projected);
  }

  public boolean equals(Object o) {
    return o != null && o.getClass() == getClass() && args.equals(((CompositeStructure) o).args);
  }

  public int hashCode() {
    return Objects.hash(getClass(), args);
  }

  public String toString() {
    return getClass().getSimpleName().replace("Operator", "").toUpperCase() + args;
  }
}
