package opencep.tree;

import java.time.*;
import java.util.*;
import opencep.base.*;

public final class StructureConstraints {
  private StructureConstraints() {}

  public static boolean inWindow(Candidate c, Duration window) {
    return Duration.between(c.first, c.last).compareTo(window) <= 0;
  }

  public static boolean sequence(PatternStructure s, Binding b) {
    if (s instanceof PrimitiveEventStructure
        || s instanceof NegationOperator
        || s instanceof KleeneClosureOperator) return true;
    var composite = (CompositeStructure) s;
    if (s instanceof OrOperator) return true;
    if (s instanceof SeqOperator) {
      var args = composite.args;
      for (int i = 0; i < args.size(); i++)
        for (int j = i + 1; j < args.size(); j++) {
          var left = present(args.get(i), b);
          var right = present(args.get(j), b);
          if (left.isEmpty() || right.isEmpty()) continue;
          var latest =
              left.stream().map(e -> e.maxTimestamp).max(Comparator.naturalOrder()).orElseThrow();
          var earliest =
              right.stream().map(e -> e.minTimestamp).min(Comparator.naturalOrder()).orElseThrow();
          if (latest.isAfter(earliest)) return false;
        }
    }
    return composite.args.stream()
        .allMatch(a -> a instanceof KleeneClosureOperator || sequence(a, b));
  }

  private static List<Event> present(PatternStructure s, Binding b) {
    return s.getAllEventNames().stream()
        .filter(b.names()::contains)
        .flatMap(n -> b.events(n).stream())
        .toList();
  }

  public static boolean legacySequence(Candidate candidate, boolean secondary) {
    for (int i = 1; i < candidate.events.size(); i++) {
      var left = candidate.events.get(i - 1);
      var right = candidate.events.get(i);
      if (left.timestamp.isAfter(right.timestamp)
          || (secondary && left.maxTimestamp.isAfter(right.maxTimestamp))) return false;
    }
    return true;
  }

  public static boolean contiguous(Pattern p, Binding b) {
    for (var names : p.consumptionPolicy.contiguousNames)
      for (int i = 1; i < names.size(); i++) {
        if (!b.names().contains(names.get(i - 1)) || !b.names().contains(names.get(i))) continue;
        var left = b.events(names.get(i - 1));
        var right = b.events(names.get(i));
        if (left.size() != 1 || right.size() != 1 || right.get(0).index - left.get(0).index != 1)
          return false;
      }
    return true;
  }
}
