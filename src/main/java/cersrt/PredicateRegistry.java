package cersrt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** One registry replaces the source's reflection and one-class-per-predicate boilerplate. */
public final class PredicateRegistry {
  private final Map<String, Function<List<String>, Guard>> factories = new HashMap<>();

  public PredicateRegistry() {
    register("IsEventTypePredicate", 1, a -> (e, r) -> e.type().equals(a.get(0)));
    register("TruePredicate", 0, a -> (e, r) -> true);
    register("FalsePredicate", 0, a -> (e, r) -> false);
    register("EQStr", 2, a -> (e, r) -> e.attribute(a.get(0)).toString().equals(a.get(1)));
    register(
        "EQAttrStr",
        2,
        a ->
            (e, r) ->
                r.containsKey(a.get(1))
                    && e.attribute(a.get(0))
                        .toString()
                        .equals(r.get(a.get(1)).attribute(a.get(0)).toString()));
    for (String operator : List.of("EQ", "NEQ", "LT", "LTE", "GT", "GTE")) {
      register(
          operator,
          2,
          a -> {
            double constant = finite(a.get(1));
            return (e, r) -> compare(operator, e.number(a.get(0)), constant);
          });
      register(
          operator + "Attr",
          2,
          a ->
              (e, r) ->
                  r.containsKey(a.get(1))
                      && compare(operator, e.number(a.get(0)), r.get(a.get(1)).number(a.get(0))));
    }
  }

  public PredicateRegistry register(String name, int arity, Function<List<String>, Guard> factory) {
    Objects.requireNonNull(factory, "factory");
    factories.put(
        Objects.requireNonNull(name, "name"),
        arguments -> {
          if (arguments.size() != arity)
            throw new IllegalArgumentException(name + " expects " + arity + " arguments");
          return Objects.requireNonNull(factory.apply(arguments), "predicate");
        });
    return this;
  }

  public Guard create(String name, List<String> arguments) {
    var factory = factories.get(name);
    if (factory == null)
      throw new IllegalArgumentException(
          "Unknown predicate: " + name + "; register a Java Guard factory first");
    return factory.apply(List.copyOf(arguments));
  }

  private static double finite(String value) {
    double result = Double.parseDouble(value);
    if (!Double.isFinite(result))
      throw new IllegalArgumentException("Non-finite predicate constant");
    return result;
  }

  private static boolean compare(String operator, double left, double right) {
    return switch (operator) {
      case "EQ" -> left == right;
      case "NEQ" -> left != right;
      case "LT" -> left < right;
      case "LTE" -> left <= right;
      case "GT" -> left > right;
      case "GTE" -> left >= right;
      default -> throw new IllegalArgumentException(operator);
    };
  }
}
