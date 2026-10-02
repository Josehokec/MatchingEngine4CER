package opencep.plan;

import java.util.*;
import opencep.base.*;

/** Immutable physical evaluation plan; scope keeps logical SEQ order after reordering. */
public final class TreePlanNode {
  public final OperatorTypes operator;
  public final PatternStructure scope;
  public final List<TreePlanNode> children;
  public final PrimitiveEventStructure event;
  public final int eventIndex;
  public final boolean unbounded;

  public TreePlanNode(
      OperatorTypes op,
      PatternStructure scope,
      List<TreePlanNode> children,
      PrimitiveEventStructure event,
      int index,
      boolean unbounded) {
    this.operator = op;
    this.scope = scope;
    this.children = List.copyOf(children);
    this.event = event;
    eventIndex = index;
    this.unbounded = unbounded;
  }

  public static TreePlanNode leaf(PrimitiveEventStructure event, int index) {
    return new TreePlanNode(OperatorTypes.LEAF, event, List.of(), event, index, false);
  }

  public static TreePlanNode node(
      OperatorTypes op, PatternStructure scope, TreePlanNode... children) {
    return new TreePlanNode(op, scope, List.of(children), null, -1, false);
  }

  public Set<String> getEventNames() {
    if (operator == OperatorTypes.LEAF) return Set.of(event.name());
    var out = new LinkedHashSet<String>();
    int end =
        operator == OperatorTypes.NEGATIVE_AND || operator == OperatorTypes.NEGATIVE_SEQ
            ? 1
            : children.size();
    for (int i = 0; i < end; i++) out.addAll(children.get(i).getEventNames());
    return out;
  }

  public List<TreePlanNode> getLeaves() {
    return operator == OperatorTypes.LEAF
        ? List.of(this)
        : children.stream().flatMap(c -> c.getLeaves().stream()).toList();
  }

  public String signature() {
    return operator
        + ":"
        + scope
        + ":"
        + eventIndex
        + ":"
        + unbounded
        + children.stream().map(TreePlanNode::signature).toList();
  }

  public String getStructureSummary() {
    return operator == OperatorTypes.LEAF
        ? event.name()
        : operator
            + "("
            + String.join(",", children.stream().map(TreePlanNode::getStructureSummary).toList())
            + ")";
  }

  public boolean isEquivalent(TreePlanNode other) {
    return signature().equals(other.signature());
  }

  public String toString() {
    return getStructureSummary();
  }

  public TreePlanNode withChildren(List<TreePlanNode> children) {
    return new TreePlanNode(operator, scope, children, event, eventIndex, unbounded);
  }
}
