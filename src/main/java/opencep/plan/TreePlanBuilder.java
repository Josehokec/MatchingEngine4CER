package opencep.plan;

import java.util.*;
import opencep.adaptive.StatisticsSnapshot;
import opencep.base.*;

/** Ports left-deep, subset-DP, interval-DP (ZStream), and iterative planners. */
public final class TreePlanBuilder {
  private final TreePlanBuilderParameters parameters;
  private final TreeCostModel costModel = new TreeCostModel();
  private Invariants invariants = new Invariants();
  private Pattern pattern;
  private StatisticsSnapshot statistics;
  private Map<String, Integer> indices;

  public TreePlanBuilder(TreePlanBuilderParameters parameters) {
    this.parameters = parameters;
  }

  public Invariants getInvariants() {
    return invariants;
  }

  public TreePlan buildTreePlan(Pattern pattern, StatisticsSnapshot statistics) {
    this.pattern = pattern;
    this.statistics =
        statistics == null
            ? StatisticsSnapshot.defaults(pattern.countPrimitiveEvents())
            : statistics;
    if (this.statistics.size() != pattern.countPrimitiveEvents())
      throw new IllegalArgumentException("Statistics dimensions differ from pattern");
    indices = new LinkedHashMap<>();
    int i = 0;
    for (var e : pattern.getPrimitiveEvents()) indices.put(e.name(), i++);
    invariants = new Invariants();
    var root = build(pattern.fullStructure);
    return new TreePlan(pattern, root, cost(root, this.statistics));
  }

  private double cost(TreePlanNode node, StatisticsSnapshot s) {
    return costModel.getPlanCost(pattern, node, s);
  }

  private TreePlanNode build(PatternStructure structure) {
    if (structure instanceof PrimitiveEventStructure p)
      return TreePlanNode.leaf(p, indices.get(p.name()));
    if (structure instanceof KleeneClosureOperator k)
      return TreePlanNode.node(OperatorTypes.KC, k, build(k.arg));
    if (structure instanceof NegationOperator)
      throw new IllegalArgumentException("NOT must be an operand of SEQ or AND");
    var composite = (CompositeStructure) structure;
    if (structure instanceof OrOperator)
      return TreePlanNode.node(
          OperatorTypes.OR,
          structure,
          composite.args.stream().map(this::build).toArray(TreePlanNode[]::new));
    var positive = new ArrayList<TreePlanNode>();
    var negatives = new ArrayList<Integer>();
    for (int i = 0; i < composite.args.size(); i++) {
      var arg = composite.args.get(i);
      if (arg instanceof NegationOperator) negatives.add(i);
      else positive.add(build(arg));
    }
    if (positive.isEmpty())
      throw new IllegalArgumentException("Each SEQ or AND needs a positive operand");
    var op = structure instanceof SeqOperator ? OperatorTypes.SEQ : OperatorTypes.AND;
    TreePlanNode root = topology(positive, op, structure);
    if (parameters.negationAlgorithm != NegationAlgorithmTypes.NAIVE_NEGATION_ALGORITHM)
      negatives.sort(
          Comparator.comparingDouble(
              i -> cost(build(((NegationOperator) composite.args.get(i)).arg), statistics)));
    // Bounded negatives precede unbounded negatives, as in the Python planners.
    negatives.sort(Comparator.comparing(i -> unbounded(composite, i)));
    for (int i : negatives) {
      var negative = build(((NegationOperator) composite.args.get(i)).arg);
      var negOp = op == OperatorTypes.SEQ ? OperatorTypes.NEGATIVE_SEQ : OperatorTypes.NEGATIVE_AND;
      if (parameters.negationAlgorithm == NegationAlgorithmTypes.LOWEST_POSITION_NEGATION_ALGORITHM
          && !unbounded(composite, i)) root = insertNegative(root, negative, composite, i, negOp);
      else
        root =
            new TreePlanNode(
                negOp, structure, List.of(root, negative), null, i, unbounded(composite, i));
    }
    return root;
  }

  private boolean unbounded(CompositeStructure structure, int i) {
    return structure instanceof AndOperator
        || structure.args.subList(i + 1, structure.args.size()).stream()
            .allMatch(NegationOperator.class::isInstance);
  }

  private TreePlanNode insertNegative(
      TreePlanNode root,
      TreePlanNode negative,
      CompositeStructure scope,
      int index,
      OperatorTypes op) {
    var required = new HashSet<String>();
    for (int i = index - 1; i >= 0; i--)
      if (!(scope.args.get(i) instanceof NegationOperator)) {
        required.addAll(Pattern.positiveNames(scope.args.get(i)));
        break;
      }
    for (int i = index + 1; i < scope.args.size(); i++)
      if (!(scope.args.get(i) instanceof NegationOperator)) {
        required.addAll(Pattern.positiveNames(scope.args.get(i)));
        break;
      }
    for (var atom : pattern.condition.extractAtomicConditions())
      if (!Collections.disjoint(atom.getEventNames(), negative.getEventNames()))
        required.addAll(atom.getEventNames());
    required.removeAll(negative.getEventNames());
    if (root.children.size() == 2
        && (root.operator == OperatorTypes.SEQ || root.operator == OperatorTypes.AND)) {
      for (int i = 0; i < 2; i++)
        if (root.children.get(i).getEventNames().containsAll(required)) {
          var children = new ArrayList<>(root.children);
          children.set(i, insertNegative(children.get(i), negative, scope, index, op));
          return root.withChildren(children);
        }
    }
    return new TreePlanNode(op, scope, List.of(root, negative), null, index, false);
  }

  private TreePlanNode join(
      TreePlanNode a, TreePlanNode b, OperatorTypes op, PatternStructure scope) {
    return TreePlanNode.node(op, scope, a, b);
  }

  private TreePlanNode leftDeep(
      List<TreePlanNode> leaves, List<Integer> order, OperatorTypes op, PatternStructure scope) {
    TreePlanNode root = leaves.get(order.get(0));
    for (int i = 1; i < order.size(); i++) root = join(root, leaves.get(order.get(i)), op, scope);
    return root;
  }

  private TreePlanNode topology(
      List<TreePlanNode> leaves, OperatorTypes op, PatternStructure scope) {
    int n = leaves.size();
    if (n == 1) return leaves.get(0);
    var natural = new ArrayList<Integer>();
    for (int i = 0; i < n; i++) natural.add(i);
    return switch (parameters.builderType) {
      case TRIVIAL_LEFT_DEEP_TREE -> leftDeep(leaves, natural, op, scope);
      case SORT_BY_FREQUENCY_LEFT_DEEP_TREE -> {
        natural.sort(Comparator.comparingDouble(i -> cost(leaves.get(i), statistics)));
        yield leftDeep(leaves, natural, op, scope);
      }
      case GREEDY_LEFT_DEEP_TREE, INVARIANT_AWARE_GREEDY_LEFT_DEEP_TREE -> leftDeep(
          leaves, greedy(leaves), op, scope);
      case LOCAL_SEARCH_LEFT_DEEP_TREE -> {
        var initial = parameters.randomInitialOrder ? natural : greedy(leaves);
        if (parameters.randomInitialOrder)
          Collections.shuffle(initial, new Random(parameters.randomSeed));
        var order =
            new IterativeImprovement(parameters.randomSeed)
                .execute(
                    parameters.stepLimit,
                    initial,
                    parameters.circleMoves,
                    o -> cost(leftDeep(leaves, o, op, scope), statistics));
        yield leftDeep(leaves, order, op, scope);
      }
      case DYNAMIC_PROGRAMMING_LEFT_DEEP_TREE -> subsetDP(leaves, op, scope, false);
      case DYNAMIC_PROGRAMMING_BUSHY_TREE -> subsetDP(leaves, op, scope, true);
      case ZSTREAM_BUSHY_TREE, INVARIANT_AWARE_ZSTREAM_BUSHY_TREE -> intervalDP(
          leaves, natural, op, scope);
      case ORDERED_ZSTREAM_BUSHY_TREE -> intervalDP(leaves, greedy(leaves), op, scope);
    };
  }

  private double changeFactor(
      TreePlanNode candidate, List<TreePlanNode> chosen, StatisticsSnapshot s) {
    var estimate =
        costModel.estimate(candidate, s, 1, Collections.newSetFromMap(new IdentityHashMap<>()));
    double factor = estimate.partialMatches();
    for (var previous : chosen)
      for (var a : candidate.getLeaves())
        for (var b : previous.getLeaves()) factor *= s.selectivity(a.eventIndex, b.eventIndex);
    return factor;
  }

  private List<Integer> greedy(List<TreePlanNode> leaves) {
    var remaining = new ArrayList<Integer>();
    for (int i = 0; i < leaves.size(); i++) remaining.add(i);
    var order = new ArrayList<Integer>();
    var chosen = new ArrayList<TreePlanNode>();
    while (!remaining.isEmpty()) {
      int best = remaining.get(0);
      double factor = changeFactor(leaves.get(best), chosen, statistics);
      for (int i : remaining) {
        double c = changeFactor(leaves.get(i), chosen, statistics);
        if (c < factor) {
          best = i;
          factor = c;
        }
      }
      var winner = leaves.get(best);
      var prefix = List.copyOf(chosen);
      for (int i : remaining)
        if (i != best) {
          var alternate = leaves.get(i);
          invariants.add(s -> changeFactor(winner, prefix, s) > changeFactor(alternate, prefix, s));
        }
      remaining.remove(Integer.valueOf(best));
      order.add(best);
      chosen.add(winner);
    }
    return order;
  }

  private TreePlanNode subsetDP(
      List<TreePlanNode> leaves, OperatorTypes op, PatternStructure scope, boolean bushy) {
    int n = leaves.size();
    if (n > 20)
      throw new IllegalArgumentException(
          "Subset DP supports at most 20 operands; use greedy or ZStream for larger patterns");
    int count = 1 << n;
    var best = new TreePlanNode[count];
    var costs = new double[count];
    Arrays.fill(costs, Double.POSITIVE_INFINITY);
    for (int i = 0; i < n; i++) {
      best[1 << i] = leaves.get(i);
      costs[1 << i] = cost(leaves.get(i), statistics);
    }
    for (int mask = 1; mask < count; mask++) {
      if (Integer.bitCount(mask) < 2) continue;
      for (int left = (mask - 1) & mask; left > 0; left = (left - 1) & mask) {
        int right = mask ^ left;
        if (right == 0
            || (!bushy && Integer.bitCount(right) != 1)
            || (bushy && (left & Integer.lowestOneBit(mask)) == 0)) continue;
        var node = join(best[left], best[right], op, scope);
        double c = cost(node, statistics);
        if (c < costs[mask]) {
          best[mask] = node;
          costs[mask] = c;
        }
      }
    }
    return best[count - 1];
  }

  private TreePlanNode intervalDP(
      List<TreePlanNode> leaves, List<Integer> order, OperatorTypes op, PatternStructure scope) {
    int n = leaves.size();
    var best = new TreePlanNode[n][n];
    for (int i = 0; i < n; i++) best[i][i] = leaves.get(order.get(i));
    for (int length = 2; length <= n; length++)
      for (int start = 0; start + length <= n; start++) {
        int end = start + length - 1;
        double bestCost = Double.POSITIVE_INFINITY;
        var alternatives = new ArrayList<TreePlanNode>();
        for (int split = start; split < end; split++) {
          var node = join(best[start][split], best[split + 1][end], op, scope);
          alternatives.add(node);
          double c = cost(node, statistics);
          if (c < bestCost) {
            bestCost = c;
            best[start][end] = node;
          }
        }
        var winner = best[start][end];
        for (var alternate : alternatives)
          if (alternate != winner) invariants.add(s -> cost(winner, s) > cost(alternate, s));
      }
    return best[0][n - 1];
  }
}
