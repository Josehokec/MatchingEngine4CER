package opencep.tree;

import java.time.Instant;
import java.util.*;
import opencep.base.*;
import opencep.misc.Utils;
import opencep.plan.*;

/** Negative joins retain positives until future events can no longer invalidate them. */
public final class NegationNode extends Node {
  public final Node positive, negative;

  private static final class Pending {
    Candidate candidate;
    final Set<String> checked = new HashSet<>();
    boolean rejected;

    Pending(Candidate candidate) {
      this.candidate = candidate;
    }
  }

  private final List<Pending> pending = new ArrayList<>();
  private final java.time.Duration negativeDelay;

  public NegationNode(
      TreePlanNode plan,
      Pattern pattern,
      TreeStorageParameters params,
      Node positive,
      Node negative) {
    super(plan, pattern, params);
    this.positive = positive;
    this.negative = negative;
    negativeDelay =
        legacy
            ? java.time.Duration.ZERO
            : pattern.window.multipliedBy(maxDelay(plan.children.get(1)));
    positive.addParent(this::onPositive);
    negative.addParent(this::onNegative);
  }

  private void onPositive(Candidate candidate) {
    if (legacy) {
      negative.advance(candidate.last, false);
      advance(candidate.last, false);
    }
    Pending state = new Pending(candidate);
    for (Candidate forbidden : negative.getPartialMatches()) check(state, forbidden);
    if (state.rejected) return;
    if (plan.unbounded && (!legacy || !(positive instanceof NegationNode n) || !n.plan.unbounded))
      pending.add(state);
    else emit(state.candidate);
  }

  private void onNegative(Candidate forbidden) {
    NegationNode target = legacy ? firstLegacy() : this;
    for (Pending state : target.pending) {
      check(state, forbidden);
      if (legacy
          && state.checked.contains(forbidden.binding.key())
          && forbidden.probability != null) {
        var combined = state.candidate.merge(forbidden, false, plan.scope.getAllEventNames(), true);
        if (combined != null
            && StructureConstraints.inWindow(combined, pattern.window)
            && (plan.operator != OperatorTypes.NEGATIVE_SEQ
                || StructureConstraints.legacySequence(combined, false))
            && condition.mayMatch(combined.binding)) state.rejected = true;
      }
    }
    target.pending.removeIf(state -> state.rejected);
  }

  private void check(Pending state, Candidate forbidden) {
    if (state.rejected || !state.checked.add(forbidden.binding.key())) return;
    Candidate combined =
        state.candidate.merge(forbidden, false, plan.scope.getAllEventNames(), legacy);
    if (combined == null
        || !StructureConstraints.inWindow(combined, pattern.window)
        || !condition.mayMatch(combined.binding)) return;
    if (plan.operator == OperatorTypes.NEGATIVE_SEQ
        && !(legacy
            ? StructureConstraints.legacySequence(combined, false)
            : StructureConstraints.sequence(plan.scope, combined.binding))) return;
    if (forbidden.probability == null) state.rejected = true;
    else
      state.candidate =
          state.candidate.withProbability(
              Utils.calculateJointProbability(
                  state.candidate.probability, 1 - forbidden.probability));
  }

  @Override
  public void advance(Instant timestamp, boolean end) {
    List<Pending> ready =
        pending.stream()
            .filter(
                state ->
                    end
                        || (timestamp != null
                            && timestamp.isAfter(
                                state.candidate.first.plus(pattern.window).plus(negativeDelay))))
            .toList();
    for (Pending state : ready) {
      pending.remove(state);
      boolean suppressed = SUPPRESS_EXPIRATION.get();
      if (legacy) SUPPRESS_EXPIRATION.set(true);
      try {
        if (!state.rejected) emit(state.candidate);
      } finally {
        SUPPRESS_EXPIRATION.set(suppressed);
      }
    }
    super.advance(timestamp, end);
  }

  private NegationNode firstLegacy() {
    return positive instanceof NegationNode n && n.plan.unbounded ? n.firstLegacy() : this;
  }

  public void flushLegacy() {
    if (plan.unbounded) firstLegacy().advance(null, true);
  }

  /** Windows of lookahead needed before this subtree can prove absence. */
  public static int maxDelay(TreePlanNode plan) {
    int delay = plan.children.stream().mapToInt(NegationNode::maxDelay).max().orElse(0);
    if ((plan.operator == OperatorTypes.NEGATIVE_AND || plan.operator == OperatorTypes.NEGATIVE_SEQ)
        && plan.unbounded)
      return Math.max(maxDelay(plan.children.get(0)), 1 + maxDelay(plan.children.get(1)));
    return delay;
  }

  public int pendingCount() {
    return pending.size();
  }
}
