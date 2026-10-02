package opencep.condition;

import java.util.*;
import java.util.function.Function;
import opencep.base.Binding;

/** A named event attribute. Lambdas can compute arbitrary payload expressions. */
public final class Variable {
  private final String name;
  private final Function<Map<String, Object>, Object> getter;
  private final String attribute;

  public Variable(String name, Function<Map<String, Object>, Object> getter) {
    this.name = Objects.requireNonNull(name);
    this.getter = Objects.requireNonNull(getter);
    attribute = null;
  }

  public Variable(String name, String attribute) {
    this.name = Objects.requireNonNull(name);
    this.attribute = Objects.requireNonNull(attribute);
    getter = payload -> payload.get(attribute);
  }

  public String name() {
    return name;
  }

  public Function<Map<String, Object>, Object> getter() {
    return getter;
  }

  public Object eval(Binding binding) {
    var events = binding.events(name);
    if (events.size() != 1)
      throw new IllegalArgumentException("Use a KC condition for a list binding: " + name);
    return getter.apply(events.get(0).payload);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Variable variable
        && name.equals(variable.name)
        && (attribute != null
            ? attribute.equals(variable.attribute)
            : variable.attribute == null && getter.equals(variable.getter));
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, attribute != null ? attribute : getter);
  }

  @Override
  public String toString() {
    return name + (attribute == null ? "" : "." + attribute);
  }
}
