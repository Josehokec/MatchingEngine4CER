package opencep.tree;

import java.util.List;
import opencep.base.Pattern;
import opencep.plan.TreePlanNode;

public final class OrNode extends Node {
  public final List<Node> children;

  public OrNode(
      TreePlanNode plan, Pattern pattern, TreeStorageParameters params, List<Node> children) {
    super(plan, pattern, params);
    this.children = List.copyOf(children);
    children.forEach(c -> c.addParent(this::emit));
  }
}
