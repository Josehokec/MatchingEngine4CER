package opencep.tree;

import opencep.base.*;
import opencep.plan.TreePlanNode;

public final class LeafNode extends Node {
  public LeafNode(TreePlanNode plan, Pattern pattern, TreeStorageParameters params) {
    super(plan, pattern, params);
  }

  public void handleEvent(Event event) {
    if (legacy) advance(event.timestamp, false);
    if (plan.event.type().equals(event.type)) emit(Candidate.leaf(plan.event.name(), event));
  }

  public String getEventType() {
    return plan.event.type();
  }

  public String getEventName() {
    return plan.event.name();
  }
}
