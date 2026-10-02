package opencep.tree;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import opencep.base.*;
import opencep.condition.*;
import opencep.plan.*;

public class BinaryNode extends Node {
  public final Node left, right;
  private final boolean sorted;
  private JoinIndex leftIndex, rightIndex;
  private Function<Candidate, Object> leftKey, rightKey;
  private RelopTypes relation;

  public BinaryNode(
      TreePlanNode plan, Pattern pattern, TreeStorageParameters params, Node left, Node right) {
    super(plan, pattern, params);
    this.left = left;
    this.right = right;
    sorted = params.sortStorage();
    left.addParent(candidate -> join(candidate, right, true));
    right.addParent(candidate -> join(candidate, left, false));
  }

  @Override
  public void setCondition(Condition condition) {
    super.setCondition(condition);
    if (!sorted
        || !(condition instanceof BaseRelationCondition || condition instanceof AndCondition))
      return;
    // KC variables bind lists and cannot use a scalar range index.
    if (hasClosure(left.plan) || hasClosure(right.plan)) return;
    for (AtomicCondition atom : condition.extractAtomicConditions()) {
      if (!(atom instanceof BaseRelationCondition binary)
          || !(binary.leftTerm instanceof Variable a)
          || !(binary.rightTerm instanceof Variable b)) continue;
      boolean forward =
          left.getEventNames().contains(a.name()) && right.getEventNames().contains(b.name());
      boolean reverse =
          left.getEventNames().contains(b.name()) && right.getEventNames().contains(a.name());
      if (!forward && !reverse) continue;
      Variable leftVariable = forward ? a : b, rightVariable = forward ? b : a;
      leftKey = candidate -> leftVariable.eval(candidate.binding);
      rightKey = candidate -> rightVariable.eval(candidate.binding);
      relation = forward ? binary.relopType : binary.relopType.opposite();
      leftIndex = new JoinIndex(leftKey);
      rightIndex = new JoinIndex(rightKey);
      left.getPartialMatches().forEach(leftIndex::add);
      right.getPartialMatches().forEach(rightIndex::add);
      break;
    }
  }

  private boolean hasClosure(TreePlanNode plan) {
    return plan.operator == OperatorTypes.KC || plan.children.stream().anyMatch(this::hasClosure);
  }

  private void join(Candidate fresh, Node other, boolean fromLeft) {
    if (legacy) {
      other.advance(fresh.last, false);
      advance(fresh.last, false);
    }
    List<Candidate> candidates;
    if (leftIndex == null) candidates = other.getPartialMatches();
    else if (fromLeft) {
      leftIndex.add(fresh);
      candidates = rightIndex.get(leftKey.apply(fresh), relation.opposite());
    } else {
      rightIndex.add(fresh);
      candidates = leftIndex.get(rightKey.apply(fresh), relation);
    }
    for (Candidate stored : candidates) {
      Candidate combined =
          fromLeft
              ? fresh.merge(stored, false, plan.scope.getAllEventNames(), legacy)
              : stored.merge(fresh, false, plan.scope.getAllEventNames(), legacy);
      if (combined != null) {
        boolean ordered =
            plan.operator != OperatorTypes.SEQ
                || (legacy
                    ? StructureConstraints.legacySequence(combined, true)
                    : StructureConstraints.sequence(plan.scope, combined.binding));
        if (ordered) emit(combined);
      }
    }
  }

  @Override
  public void advance(Instant timestamp, boolean end) {
    super.advance(timestamp, end);
    if (timestamp != null && leftIndex != null) {
      Instant earliest = timestamp.minus(retentionWindow);
      leftIndex.cleanExpired(earliest);
      rightIndex.cleanExpired(earliest);
    }
  }
}
