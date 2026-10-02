package opencep.tree;

import java.util.*;
import opencep.base.*;
import opencep.misc.Utils;
import opencep.plan.TreePlanNode;

public final class KleeneClosureNode extends Node {
  public final Node child;
  private final KleeneClosureOperator closure;

  public KleeneClosureNode(
      TreePlanNode plan, Pattern pattern, TreeStorageParameters params, Node child) {
    super(plan, pattern, params);
    this.child = child;
    closure = (KleeneClosureOperator) plan.scope;
    child.addParent(this::handle);
  }

  private void handle(Candidate fresh) {
    if (legacy) child.advance(fresh.last, false);
    var previous =
        child.getPartialMatches().stream()
            .filter(c -> c != fresh)
            .sorted(Comparator.comparingLong(c -> c.serial))
            .toList();
    int max = closure.maxSize == null ? previous.size() + 1 : closure.maxSize;
    Utils.powerset(
        previous,
        Math.max(0, closure.minSize - 1),
        Math.min(previous.size(), max - 1),
        subset -> {
          var parts = new ArrayList<>(subset);
          parts.add(fresh);
          Candidate merged = parts.get(0);
          for (int i = 1; i < parts.size() && merged != null; i++)
            merged = merged.merge(parts.get(i), true, null, legacy);
          if (merged != null) {
            var aggregate = new AggregatedEvent(merged.events, legacy);
            emit(
                new Candidate(
                    merged.binding.markClosure(getEventNames(), aggregate.primitiveEvents()),
                    List.of(aggregate),
                    merged.probability));
          }
        });
  }
}
