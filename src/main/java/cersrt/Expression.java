package cersrt;

import java.util.List;
import java.util.Objects;

/** Symbolic regular expressions with memory, in an immutable Java representation. */
public sealed interface Expression {
  record Atom(Guard guard, String register) implements Expression {
    public Atom {
      Objects.requireNonNull(guard, "guard");
      if (register != null && register.isBlank())
        throw new IllegalArgumentException("Empty register");
    }
  }

  record Epsilon() implements Expression {}

  record Sequence(List<Expression> children) implements Expression {
    public Sequence {
      children = operands(children, 2);
    }
  }

  record Choice(List<Expression> children) implements Expression {
    public Choice {
      children = operands(children, 2);
    }
  }

  record Repeat(Expression child) implements Expression {
    public Repeat {
      Objects.requireNonNull(child, "child");
    }
  }

  enum Selection {
    STRICT,
    ANY,
    NEXT
  }

  /** Selection applies to this node's immediate sequence gaps or repetition boundaries. */
  record Selected(Expression child, Selection selection) implements Expression {
    public Selected {
      Objects.requireNonNull(child, "child");
      Objects.requireNonNull(selection, "selection");
    }
  }

  private static List<Expression> operands(List<Expression> children, int minimum) {
    List<Expression> result = List.copyOf(children);
    if (result.size() < minimum)
      throw new IllegalArgumentException("At least " + minimum + " operands required");
    return result;
  }

  static Expression atom(Guard guard) {
    return new Atom(guard, null);
  }

  static Expression atom(Guard guard, String register) {
    return new Atom(guard, register);
  }

  static Expression type(String type) {
    return atom((event, registers) -> event.type().equals(type));
  }

  static Expression epsilon() {
    return new Epsilon();
  }

  static Expression seq(Expression... children) {
    return new Sequence(List.of(children));
  }

  static Expression choice(Expression... children) {
    return new Choice(List.of(children));
  }

  static Expression star(Expression child) {
    return new Repeat(child);
  }

  static Expression any(Expression child) {
    return new Selected(child, Selection.ANY);
  }

  static Expression next(Expression child) {
    return new Selected(child, Selection.NEXT);
  }
}
