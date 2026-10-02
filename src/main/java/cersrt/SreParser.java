package cersrt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses Wayeb's prefix .sre syntax, including register writes and query metadata. */
public final class SreParser {
  private final PredicateRegistry registry;
  private final boolean legacyFirstChild;

  public SreParser() {
    this(new PredicateRegistry(), false);
  }

  public SreParser(PredicateRegistry registry) {
    this(registry, false);
  }

  public SreParser(PredicateRegistry registry, boolean legacyFirstChild) {
    this.registry = java.util.Objects.requireNonNull(registry);
    this.legacyFirstChild = legacyFirstChild;
  }

  public List<Query> parse(String source) {
    return parse(source, -1);
  }

  /** The supplied value resolves {window:TIMESTAMP} in the published query templates. */
  public List<Query> parse(String source, long templateWindow) {
    return new Reader(source, templateWindow).queries();
  }

  private final class Reader {
    private final String source;
    private final long templateWindow;
    private int offset;
    private final Set<String> declarations = new HashSet<>();
    private final Set<String> references = new HashSet<>();
    private final Map<String, Guard> guardCache = new LinkedHashMap<>();

    Reader(String source, long templateWindow) {
      this.source = source;
      this.templateWindow = templateWindow;
    }

    List<Query> queries() {
      List<Query> queries = new ArrayList<>();
      do {
        declarations.clear();
        references.clear();
        Expression expression = expression();
        if (!declarations.containsAll(references)) {
          var missing = new HashSet<>(references);
          missing.removeAll(declarations);
          throw error("Undeclared registers: " + missing);
        }
        long window = 0;
        int order = 0;
        String partition = "$";
        Query.WindowType type = Query.WindowType.COUNT;
        Set<String> metadata = new HashSet<>();
        while (take('{')) {
          String key = word();
          expect(':');
          String value = word();
          expect('}');
          if (!metadata.add(key)) throw error("Repeated metadata: " + key);
          switch (key) {
            case "window" -> {
              if (value.equals("TIMESTAMP")) {
                if (templateWindow < 0)
                  throw error("Supply a window value for the TIMESTAMP template");
                window = templateWindow;
              } else window = nonnegative(value);
            }
            case "windowType" -> type =
                switch (value) {
                  case "time" -> Query.WindowType.TIME;
                  case "count" -> Query.WindowType.COUNT;
                  default -> throw error("windowType must be time or count");
                };
            case "order" -> order = Math.toIntExact(nonnegative(value));
            case "partitionBy" -> partition = value;
            default -> throw error("Unknown metadata: " + key);
          }
        }
        queries.add(new Query(expression, window, type, partition, order));
      } while (take('&'));
      space();
      if (offset != source.length()) throw error("Unexpected input");
      return List.copyOf(queries);
    }

    Expression expression() {
      space();
      if (offset >= source.length()) throw error("Expected expression");
      char symbol = source.charAt(offset);
      if (";+*#@!".indexOf(symbol) >= 0) {
        offset++;
        expect('(');
        List<Expression> children = new ArrayList<>();
        if (!take(')')) {
          do {
            children.add(expression());
          } while (take(','));
          expect(')');
        }
        if (children.isEmpty()) throw error("Operator requires operands");
        return switch (symbol) {
          case ';' -> new Expression.Sequence(children);
          case '+' -> new Expression.Choice(children);
          case '*' -> Expression.star(
              legacyFirstChild || children.size() == 1
                  ? children.get(0)
                  : new Expression.Sequence(children));
          case '#', '@' -> {
            if (children.size() != 1) throw error("Selection operator requires one operand");
            yield symbol == '#'
                ? Expression.any(children.get(0))
                : Expression.next(children.get(0));
          }
          case '!' -> throw error(
              "Regular-language complement (!) is unsupported by the source NSRA matcher; use"
                  + " Boolean predicate negation (-) for event guards");
          default -> throw error("Unknown operator");
        };
      }
      if (ahead("EpsilonPredicate")) {
        word();
        if (take('(')) expect(')');
        return Expression.epsilon();
      }
      Guard guard = sentence();
      String register = null;
      if (take('[')) {
        register = quoted();
        expect(']');
        if (register.isBlank() || !declarations.add(register))
          throw error("Repeated or empty register declaration: " + register);
      }
      return Expression.atom(guard, register);
    }

    Guard sentence() {
      if (take('-')) {
        Guard inner = sentence();
        return (e, r) -> !inner.test(e, r);
      }
      boolean and = take('^');
      if (and || take('|')) {
        expect('(');
        List<Guard> children = new ArrayList<>();
        if (!take(')')) {
          do {
            children.add(sentence());
          } while (take(','));
          expect(')');
        }
        return (e, r) -> {
          for (Guard child : children) if (child.test(e, r) != and) return !and;
          return and;
        };
      }
      String name = word();
      List<String> arguments = new ArrayList<>();
      List<Boolean> quotedTerms = new ArrayList<>();
      if (take('(') && !take(')')) {
        do {
          space();
          boolean quoted = offset < source.length() && source.charAt(offset) == '"';
          String value = quoted ? quoted() : word();
          arguments.add(value);
          quotedTerms.add(quoted);
          if (quoted) references.add(value);
        } while (take(','));
        expect(')');
      }
      String key = name + arguments + quotedTerms;
      return guardCache.computeIfAbsent(key, ignored -> registry.create(name, arguments));
    }

    private long nonnegative(String value) {
      try {
        long result = Long.parseLong(value);
        if (result < 0) throw error("Negative metadata");
        return result;
      } catch (NumberFormatException exception) {
        throw error("Expected nonnegative integer: " + value);
      }
    }

    private String word() {
      space();
      int start = offset;
      while (offset < source.length()) {
        char c = source.charAt(offset);
        if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == '$') offset++;
        else break;
      }
      if (start == offset) throw error("Expected identifier or number");
      return source.substring(start, offset);
    }

    private String quoted() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (offset < source.length()) {
        char c = source.charAt(offset++);
        if (c == '"') return result.toString();
        if (c == '\\') {
          if (offset == source.length()) throw error("Unterminated string escape");
          c = source.charAt(offset++);
          if (c != '"' && c != '\\') throw error("Unsupported string escape");
        }
        result.append(c);
      }
      throw error("Unterminated string");
    }

    private boolean ahead(String value) {
      space();
      return source.startsWith(value, offset);
    }

    private void expect(char value) {
      if (!take(value)) throw error("Expected '" + value + "'");
    }

    private boolean take(char value) {
      space();
      if (offset < source.length() && source.charAt(offset) == value) {
        offset++;
        return true;
      }
      return false;
    }

    private void space() {
      while (offset < source.length()) {
        if (Character.isWhitespace(source.charAt(offset))) offset++;
        else if (source.startsWith("//", offset)) {
          while (offset < source.length() && source.charAt(offset) != '\n') offset++;
        } else break;
      }
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(message + " at offset " + offset);
    }
  }
}
