package opencep.tree;

import opencep.base.Pattern;
import opencep.plan.TreePlanNode;

public final class AndNode extends BinaryNode {
  public AndNode(
      TreePlanNode plan, Pattern pattern, TreeStorageParameters params, Node left, Node right) {
    super(plan, pattern, params, left, right);
  }
}
