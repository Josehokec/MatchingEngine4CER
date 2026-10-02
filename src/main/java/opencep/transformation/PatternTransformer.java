package opencep.transformation;

import java.util.*;
import opencep.base.*;

public final class PatternTransformer {
  private final PatternTransformationRules rule;

  public PatternTransformer(PatternTransformationRules rule) {
    this.rule = rule;
  }

  public List<PatternStructure> transform(PatternStructure s) {
    if (rule == PatternTransformationRules.INNER_OR_PATTERN)
      return s instanceof OrOperator o ? o.args : List.of(s);
    return List.of(recursive(s));
  }

  private PatternStructure composite(CompositeStructure old, List<PatternStructure> args) {
    var array = args.toArray(PatternStructure[]::new);
    return old instanceof SeqOperator
        ? new SeqOperator(array)
        : old instanceof AndOperator ? new AndOperator(array) : new OrOperator(array);
  }

  private PatternStructure recursive(PatternStructure s) {
    if (s instanceof PrimitiveEventStructure) return s;
    if (s instanceof KleeneClosureOperator k)
      return new KleeneClosureOperator(recursive(k.arg), k.minSize, k.maxSize);
    if (s instanceof NegationOperator n) {
      var arg = recursive(n.arg);
      if (rule == PatternTransformationRules.NOT_NOT_PATTERN
          && arg instanceof NegationOperator inner) return inner.arg;
      if (rule == PatternTransformationRules.NOT_AND_PATTERN && arg instanceof AndOperator a)
        return new OrOperator(
            a.args.stream().map(NegationOperator::new).toArray(PatternStructure[]::new));
      if (rule == PatternTransformationRules.NOT_OR_PATTERN && arg instanceof OrOperator o)
        return new AndOperator(
            o.args.stream().map(NegationOperator::new).toArray(PatternStructure[]::new));
      return new NegationOperator(arg);
    }
    var c = (CompositeStructure) s;
    var children = c.args.stream().map(this::recursive).toList();
    if (rule == PatternTransformationRules.AND_AND_PATTERN) {
      var flat = new ArrayList<PatternStructure>();
      for (var child : children)
        if (child.getClass() == s.getClass()) flat.addAll(((CompositeStructure) child).args);
        else flat.add(child);
      return composite(c, flat);
    }
    if (rule == PatternTransformationRules.TOPMOST_OR_PATTERN && !(s instanceof OrOperator)) {
      for (int i = 0; i < children.size(); i++)
        if (children.get(i) instanceof OrOperator or) {
          var alternatives = new ArrayList<PatternStructure>();
          for (var option : or.args) {
            var args = new ArrayList<>(children);
            args.set(i, option);
            alternatives.add(composite(c, args));
          }
          return new OrOperator(alternatives.toArray(PatternStructure[]::new));
        }
    }
    return composite(c, children);
  }
}
