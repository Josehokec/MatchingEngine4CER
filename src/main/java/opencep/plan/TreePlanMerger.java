package opencep.plan;

import java.util.*;
import opencep.adaptive.StatisticsSnapshot;

/** Interns equivalent plan subtrees and searches join orders to improve shared cost. */
public final class TreePlanMerger {
  private final MultiPatternTreePlanMergeApproaches approach;
  private final LocalSearchParameters search;

  public TreePlanMerger(
      MultiPatternTreePlanMergeApproaches approach, LocalSearchParameters search) {
    this.approach = approach;
    this.search = search;
  }

  public List<TreePlan> mergeTreePlans(List<TreePlan> plans) {
    if (approach == MultiPatternTreePlanMergeApproaches.NONE) return List.copyOf(plans);
    if (approach == MultiPatternTreePlanMergeApproaches.TREE_PLAN_LOCAL_SEARCH)
      return localSearch(plans);
    return intern(
        plans, approach == MultiPatternTreePlanMergeApproaches.TREE_PLAN_TRIVIAL_SHARING_LEAVES);
  }

  private List<TreePlan> intern(List<TreePlan> plans, boolean onlyLeaves) {
    var pool = new HashMap<String, TreePlanNode>();
    var out = new ArrayList<TreePlan>();
    for (var p : plans)
      out.add(new TreePlan(p.pattern(), intern(p.root(), pool, onlyLeaves), p.cost()));
    return List.copyOf(out);
  }

  private TreePlanNode intern(
      TreePlanNode node, Map<String, TreePlanNode> pool, boolean onlyLeaves) {
    var children = node.children.stream().map(c -> intern(c, pool, onlyLeaves)).toList();
    var copy = node.withChildren(children);
    if (onlyLeaves && node.operator != OperatorTypes.LEAF) return copy;
    return pool.computeIfAbsent(copy.signature(), k -> copy);
  }

  private double totalCost(List<TreePlan> plans) {
    var visited = Collections.newSetFromMap(new IdentityHashMap<TreePlanNode, Boolean>());
    double total = 0;
    var model = new TreeCostModel();
    for (var plan : plans) {
      var p = plan.pattern();
      var s =
          p.getStatistics() == null
              ? StatisticsSnapshot.defaults(p.countPrimitiveEvents())
              : p.getStatistics();
      total += model.estimate(plan.root(), s, p.window.toNanos() / 1e9, visited).total();
    }
    return total;
  }

  private String key(List<TreePlan> plans) {
    return plans.stream().map(p -> p.root().signature()).toList().toString();
  }

  private List<TreePlan> localSearch(List<TreePlan> plans) {
    var random = new Random(search.seed());
    var current = intern(plans, false);
    var best = current;
    double currentCost = totalCost(current),
        bestCost = currentCost,
        temperature = Math.max(1, currentCost);
    var tabu = new ArrayDeque<String>();
    var tabuSet = new HashSet<String>();
    for (int step = 0; step < search.stepLimit(); step++) {
      List<TreePlan> candidate = null;
      double candidateCost = Double.POSITIVE_INFINITY;
      for (int neighbor = 0; neighbor < search.neighborhoodSize(); neighbor++) {
        var next = new ArrayList<>(current);
        int i = random.nextInt(next.size());
        var plan = next.get(i);
        var p = plan.pattern();
        var parameters =
            new TreePlanBuilderParameters(
                TreePlanBuilderTypes.LOCAL_SEARCH_LEFT_DEEP_TREE,
                NegationAlgorithmTypes.NAIVE_NEGATION_ALGORITHM,
                Math.max(1, step + 1),
                random.nextLong(),
                false,
                true);
        next.set(i, new TreePlanBuilder(parameters).buildTreePlan(p, p.getStatistics()));
        var merged = intern(next, false);
        double cost = totalCost(merged);
        if ((!tabuSet.contains(key(merged)) || cost < bestCost) && cost < candidateCost) {
          candidate = merged;
          candidateCost = cost;
        }
      }
      if (candidate == null) break;
      boolean accept =
          search.approach() == LocalSearchParameters.Approach.TABU_SEARCH
              || candidateCost <= currentCost
              || random.nextDouble() < Math.exp((currentCost - candidateCost) / temperature);
      if (accept) {
        current = candidate;
        currentCost = candidateCost;
        String key = key(current);
        if (tabuSet.add(key)) tabu.addLast(key);
        if (tabu.size() > search.tabuCapacity()) tabuSet.remove(tabu.removeFirst());
      }
      if (currentCost < bestCost) {
        best = current;
        bestCost = currentCost;
      }
      temperature *= search.cooling();
    }
    return best;
  }
}
