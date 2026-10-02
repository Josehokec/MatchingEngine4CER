package opencep;

import java.util.*;
import opencep.condition.BaseRelationCondition;

/** Test-only interpreter for Python lambda ASTs exported from upstream tests. */
final class FixtureExpressions {
  @SuppressWarnings("unchecked")
  static Object call(Map<String, Object> function, List<?> arguments) {
    var environment = new HashMap<String, Object>();
    var names = (List<String>) function.get("args");
    if (function.get("free") instanceof Map<?, ?> free)
      free.forEach((k, v) -> environment.put(k.toString(), v));
    for (int i = 0; i < names.size(); i++) environment.put(names.get(i), arguments.get(i));
    return expression(map(function.get("body")), environment);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  static List<Object> list(Object o) {
    return (List<Object>) o;
  }

  static boolean truth(Object o) {
    return o != null
        && (!(o instanceof Boolean b) || b)
        && (!(o instanceof Number n) || n.doubleValue() != 0)
        && (!(o instanceof Collection<?> c) || !c.isEmpty());
  }

  static double number(Object o) {
    return ((Number) o).doubleValue();
  }

  static Object expression(Map<String, Object> node, Map<String, Object> env) {
    String kind = (String) node.get("node");
    return switch (kind) {
      case "Constant" -> node.get("value");
      case "Name" -> env.containsKey(node.get("name"))
          ? env.get(node.get("name"))
          : node.get("name");
      case "Attribute" -> {
        if (node.get("attribute").equals("INDEX_ATTRIBUTE_NAME"))
          yield opencep.base.Event.INDEX_ATTRIBUTE_NAME;
        throw new IllegalArgumentException("Unknown attribute " + node);
      }
      case "Subscript" -> {
        Object v = expression(map(node.get("value")), env),
            key = expression(map(node.get("slice")), env);
        yield v instanceof Map<?, ?> m ? m.get(key) : ((List<?>) v).get(((Number) key).intValue());
      }
      case "BinOp" -> {
        Object a = expression(map(node.get("left")), env),
            b = expression(map(node.get("right")), env);
        yield switch ((String) node.get("op")) {
          case "Add" -> a instanceof String ? a.toString() + b : number(a) + number(b);
          case "Sub" -> number(a) - number(b);
          case "Mult" -> number(a) * number(b);
          case "Div" -> number(a) / number(b);
          case "Mod" -> number(a) % number(b);
          case "Pow" -> Math.pow(number(a), number(b));
          case "FloorDiv" -> Math.floor(number(a) / number(b));
          default -> throw new IllegalArgumentException("Unknown arithmetic " + node);
        };
      }
      case "Compare" -> {
        Object a = expression(map(node.get("left")), env);
        var operands = list(node.get("comparators"));
        var ops = list(node.get("ops"));
        boolean ok = true;
        for (int i = 0; i < operands.size(); i++) {
          Object b = expression(map(operands.get(i)), env);
          String op = ops.get(i).toString();
          int cmp =
              (op.equals("Is") || op.equals("IsNot"))
                  ? 0
                  : BaseRelationCondition.compareValues(a, b);
          ok &=
              switch (op) {
                case "Eq" -> cmp == 0;
                case "NotEq" -> cmp != 0;
                case "Lt" -> cmp < 0;
                case "LtE" -> cmp <= 0;
                case "Gt" -> cmp > 0;
                case "GtE" -> cmp >= 0;
                case "Is" -> a == b;
                case "IsNot" -> a != b;
                default -> throw new IllegalArgumentException("Unknown comparison " + op);
              };
          a = b;
          if (!ok) break;
        }
        yield ok;
      }
      case "BoolOp" -> {
        boolean and = node.get("op").equals("And"), result = and;
        for (var value : list(node.get("values"))) {
          boolean x = truth(expression(map(value), env));
          if (and && !x) {
            result = false;
            break;
          }
          if (!and && x) {
            result = true;
            break;
          }
        }
        yield result;
      }
      case "UnaryOp" -> {
        Object v = expression(map(node.get("value")), env);
        yield switch ((String) node.get("op")) {
          case "Not" -> !truth(v);
          case "USub" -> -number(v);
          case "UAdd" -> number(v);
          default -> throw new IllegalArgumentException("Unknown unary operation");
        };
      }
      case "IfExp" -> expression(
          map(node.get(truth(expression(map(node.get("test")), env)) ? "yes" : "no")), env);
      case "List" -> list(node.get("values")).stream().map(x -> expression(map(x), env)).toList();
      case "Call" -> {
        var function = map(node.get("function"));
        var args = list(node.get("args")).stream().map(x -> expression(map(x), env)).toList();
        String name = (String) function.get("name");
        yield switch (name) {
          case "abs" -> Math.abs(number(args.get(0)));
          case "len" -> args.get(0) instanceof Collection<?> c
              ? c.size()
              : args.get(0).toString().length();
          case "int" -> ((Number) args.get(0)).longValue();
          case "float" -> number(args.get(0));
          case "max" -> args.stream().mapToDouble(FixtureExpressions::number).max().orElseThrow();
          case "min" -> args.stream().mapToDouble(FixtureExpressions::number).min().orElseThrow();
          default -> throw new IllegalArgumentException("Unknown function " + function);
        };
      }
      default -> throw new IllegalArgumentException("Unknown fixture expression " + node);
    };
  }
}
