package opencep.tree;

import java.time.Duration;
import java.util.*;
import opencep.base.*;
import opencep.condition.Condition;
import opencep.plan.*;

/** A per-workload registry, never a static cache across unrelated CEP runs. */
public final class NodePool {
  private record Conditions(Condition here, List<Conditions> descendants) {}

  private record Key(String structure, Conditions conditions, Duration window, Double confidence) {}

  private final Map<Key, Node> nodes = new HashMap<>();
  private final boolean onlyLeaves;

  public NodePool(boolean onlyLeaves) {
    this.onlyLeaves = onlyLeaves;
  }

  private Key key(TreePlanNode plan, Pattern pattern, Map<TreePlanNode, Condition> assigned) {
    if (onlyLeaves && plan.operator != OperatorTypes.LEAF) return null;
    var policy = pattern.consumptionPolicy;
    if (policy.strategy != opencep.misc.SelectionStrategies.MATCH_ANY
        || !policy.freezeNames.isEmpty()
        || !policy.contiguousNames.isEmpty()) return null;
    return new Key(signature(plan), footprint(plan, assigned), pattern.window, pattern.confidence);
  }

  private Conditions footprint(TreePlanNode plan, Map<TreePlanNode, Condition> assigned) {
    return new Conditions(
        assigned.getOrDefault(plan, new opencep.condition.TrueCondition()),
        plan.children.stream().map(child -> footprint(child, assigned)).toList());
  }

  private String signature(TreePlanNode plan) {
    Set<String> aliases = new HashSet<>();
    plan.getLeaves().forEach(leaf -> aliases.add(leaf.event.name()));
    PatternStructure projected = plan.scope.getStructureProjection(aliases);
    return plan.operator
        + ":"
        + projected
        + ":"
        + plan.unbounded
        + plan.children.stream().map(this::signature).toList();
  }

  public Node find(TreePlanNode plan, Pattern pattern, Map<TreePlanNode, Condition> assigned) {
    Key key = key(plan, pattern, assigned);
    return key == null ? null : nodes.get(key);
  }

  public void register(Node node, Pattern pattern, Map<TreePlanNode, Condition> assigned) {
    Key key = key(node.plan, pattern, assigned);
    if (key != null) nodes.put(key, node);
  }

  public int size() {
    return nodes.size();
  }
}
