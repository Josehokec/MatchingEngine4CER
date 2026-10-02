package opencep.tree;

import opencep.base.Pattern;
import opencep.plan.TreePlanNode;

public final class SeqNode extends BinaryNode {
  public SeqNode(
      TreePlanNode plan, Pattern pattern, TreeStorageParameters params, Node left, Node right) {
    super(plan, pattern, params, left, right);
  }
}
