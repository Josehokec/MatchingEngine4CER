package opencep.plan;

import java.util.*;
import opencep.adaptive.StatisticsSnapshot;
import opencep.base.Pattern;

public final class TreeCostModel {
  public record Cost(Set<Integer> indices, double partialMatches, double total) {}

  public double getPlanCost(Pattern pattern, TreePlanNode plan, StatisticsSnapshot stats) {
    return estimate(
            plan,
            stats,
            pattern.window.toNanos() / 1e9,
            Collections.newSetFromMap(new IdentityHashMap<>()))
        .total;
  }

  public Cost estimate(
      TreePlanNode node, StatisticsSnapshot s, double seconds, Set<TreePlanNode> visited) {
    if (!visited.add(node)) return new Cost(Set.of(), 0, 0);
    if (node.operator == OperatorTypes.LEAF) {
      double pm =
          seconds * s.rate(node.eventIndex) * s.selectivity(node.eventIndex, node.eventIndex);
      return new Cost(Set.of(node.eventIndex), pm, pm);
    }
    if (node.operator == OperatorTypes.KC)
      return estimate(node.children.get(0), s, seconds, visited);
    var parts = node.children.stream().map(c -> estimate(c, s, seconds, visited)).toList();
    var indices = new LinkedHashSet<Integer>();
    double total = 0, pm = 1;
    for (var part : parts) {
      indices.addAll(part.indices);
      total += part.total;
      pm *= part.partialMatches;
    }
    if (node.operator == OperatorTypes.OR)
      pm = parts.stream().mapToDouble(Cost::partialMatches).sum();
    else {
      double sel = 1;
      for (int i = 0; i < parts.size(); i++)
        for (int j = i + 1; j < parts.size(); j++)
          for (int a : parts.get(i).indices)
            for (int b : parts.get(j).indices) sel *= s.selectivity(a, b);
      pm =
          node.operator == OperatorTypes.NEGATIVE_SEQ || node.operator == OperatorTypes.NEGATIVE_AND
              ? parts.get(0).partialMatches * (1 - sel)
              : pm * sel;
    }
    return new Cost(Set.copyOf(indices), pm, total + pm);
  }
}
