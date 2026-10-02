package opencep.tree;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import opencep.base.*;
import opencep.misc.*;
import opencep.plan.*;

public final class Tree {
  private final NodePool pool;
  private final boolean legacy;
  private final java.time.Duration retentionWindow;
  private final Pattern pattern;
  private final Map<TreePlanNode, opencep.condition.Condition> conditions = new IdentityHashMap<>();
  private final Node root;
  private final List<Node> nodes = new ArrayList<>();
  private final List<LeafNode> leaves = new ArrayList<>();
  private final Map<Long, Instant> consumed = new HashMap<>();
  private final Map<String, Event> freezers = new HashMap<>();
  private final Map<String, Set<String>> freezeMap = new LinkedHashMap<>();
  private final Consumer<PatternMatch> output;
  private final Map<String, Instant> reported = new HashMap<>();

  public Tree(TreePlan plan, TreeStorageParameters storage, Consumer<PatternMatch> output) {
    this(plan, storage, output, null);
  }

  public Tree(
      TreePlan plan, TreeStorageParameters storage, Consumer<PatternMatch> output, NodePool pool) {
    this.pool = pool;
    legacy = storage.legacySemantics();
    retentionWindow = plan.pattern().window.multipliedBy(NegationNode.maxDelay(plan.root()) + 1L);
    pattern = plan.pattern();
    this.output = output;
    assign(
        plan.root(),
        legacy ? legacyCondition(pattern.fullStructure, pattern.condition) : pattern.condition);
    root = build(plan.root(), storage);
    root.addParent(this::report);
    for (var name : pattern.consumptionPolicy.freezeNames) {
      var disabled = new HashSet<String>();
      for (var sequence : pattern.extractFlatSequences())
        if (sequence.contains(name))
          disabled.addAll(sequence.subList(0, sequence.indexOf(name) + 1));
      if (!disabled.isEmpty()) freezeMap.put(name, disabled);
    }
  }

  private opencep.condition.Condition legacyCondition(
      PatternStructure structure, opencep.condition.Condition original) {
    var additions = new ArrayList<opencep.condition.Condition>();
    additions.add(original);
    legacyContiguity(structure, additions);
    return new opencep.condition.AndCondition(
        additions.toArray(opencep.condition.Condition[]::new));
  }

  private void legacyContiguity(
      PatternStructure structure, List<opencep.condition.Condition> additions) {
    if (!(structure instanceof CompositeStructure composite)) return;
    composite.args.forEach(a -> legacyContiguity(a, additions));
    if (!(structure instanceof SeqOperator)) return;
    for (var names : pattern.consumptionPolicy.contiguousNames)
      for (int i = 0; i + 1 < names.size() && i + 1 < composite.args.size(); i++) {
        if (!(composite.args.get(i) instanceof PrimitiveEventStructure a)
            || !(composite.args.get(i + 1) instanceof PrimitiveEventStructure b)) continue;
        for (int j = 0; j + 1 < composite.args.size(); j++)
          if (composite.args.get(j) instanceof PrimitiveEventStructure found
              && found.name().equals(names.get(i)))
            additions.add(
                new opencep.condition.BinaryCondition(
                    new opencep.condition.Variable(a.name(), Event.INDEX_ATTRIBUTE_NAME),
                    new opencep.condition.Variable(b.name(), Event.INDEX_ATTRIBUTE_NAME),
                    (x, y) -> ((Number) x).longValue() == ((Number) y).longValue() - 1));
      }
  }

  private void assign(TreePlanNode node, opencep.condition.Condition condition) {
    if (condition instanceof opencep.condition.AndCondition and) {
      and.conditions.forEach(c -> assign(node, c));
      return;
    }
    for (var child : node.children) {
      var names = new HashSet<String>();
      child.getLeaves().forEach(l -> names.add(l.event.name()));
      if (names.containsAll(condition.getEventNames())
          && (!(condition instanceof opencep.condition.KCCondition)
              || containsClosure(child, condition.getEventNames()))) {
        assign(child, condition);
        return;
      }
    }
    conditions.merge(node, condition, (a, b) -> new opencep.condition.AndCondition(a, b));
  }

  private boolean containsClosure(TreePlanNode node, Set<String> names) {
    return (node.operator == OperatorTypes.KC && node.getEventNames().containsAll(names))
        || node.children.stream().anyMatch(c -> containsClosure(c, names));
  }

  private Node build(TreePlanNode plan, TreeStorageParameters params) {
    var condition = conditions.getOrDefault(plan, new opencep.condition.TrueCondition());
    var existing = pool == null ? null : pool.find(plan, pattern, conditions);
    if (existing != null) {
      registerExisting(existing);
      return existing;
    }
    var children = plan.children.stream().map(c -> build(c, params)).toList();
    Node n =
        switch (plan.operator) {
          case LEAF -> new LeafNode(plan, pattern, params);
          case SEQ -> new SeqNode(plan, pattern, params, children.get(0), children.get(1));
          case AND -> new AndNode(plan, pattern, params, children.get(0), children.get(1));
          case OR -> new OrNode(plan, pattern, params, children);
          case KC -> new KleeneClosureNode(plan, pattern, params, children.get(0));
          case NEGATIVE_SEQ, NEGATIVE_AND -> new NegationNode(
              plan, pattern, params, children.get(0), children.get(1));
        };
    n.setCondition(condition);
    n.ensureRetentionWindow(retentionWindow);
    if (pool != null) pool.register(n, pattern, conditions);
    nodes.add(n);
    if (n instanceof LeafNode leaf) leaves.add(leaf);
    return n;
  }

  private void registerExisting(Node node) {
    node.ensureRetentionWindow(retentionWindow);
    if (nodes.contains(node)) return;
    if (node instanceof BinaryNode n) {
      registerExisting(n.left);
      registerExisting(n.right);
    } else if (node instanceof KleeneClosureNode n) registerExisting(n.child);
    else if (node instanceof NegationNode n) {
      registerExisting(n.positive);
      registerExisting(n.negative);
    } else if (node instanceof OrNode n) n.children.forEach(this::registerExisting);
    nodes.add(node);
    if (node instanceof LeafNode leaf) leaves.add(leaf);
  }

  private void report(Candidate candidate) {
    var policy = pattern.consumptionPolicy;
    if (policy.strategy == SelectionStrategies.MATCH_SINGLE
        && candidate.binding.allEvents().stream()
            .anyMatch(e -> policy.appliesTo(e.type) && consumed.containsKey(e.index))) return;
    if (!legacy && reported.putIfAbsent(candidate.binding.key(), candidate.first) != null) return;
    if (policy.strategy == SelectionStrategies.MATCH_SINGLE)
      candidate.binding.allEvents().stream()
          .filter(e -> policy.appliesTo(e.type))
          .forEach(e -> consumed.put(e.index, e.timestamp));
    freezers.values().removeIf(candidate.binding.allEvents()::contains);
    var match = candidate.toMatch();
    if (pattern.id != null) match.addPatternId(pattern.id);
    output.accept(match);
  }

  public void handleEvent(Event event) {
    freezers.values().removeIf(e -> event.timestamp.isAfter(e.timestamp.plus(pattern.window)));
    for (var leaf : leaves)
      if (leaf.getEventType().equals(event.type)) {
        boolean blocked =
            freezers.keySet().stream()
                .anyMatch(name -> freezeMap.get(name).contains(leaf.getEventName()));
        if (blocked) continue;
        if (freezeMap.containsKey(leaf.getEventName())) freezers.put(leaf.getEventName(), event);
        leaf.handleEvent(event);
      }
    advance(event.timestamp, false);
  }

  public void advance(Instant timestamp, boolean end) {
    if (legacy) {
      if (end && root instanceof NegationNode negative) negative.flushLegacy();
      return;
    }
    // Flush negative nodes in child-to-parent order, then clean all storages.
    for (var node : nodes) if (node instanceof NegationNode) node.advance(timestamp, end);
    for (var node : nodes) if (!(node instanceof NegationNode)) node.advance(timestamp, end);
    if (timestamp != null) {
      var earliest = timestamp.minus(pattern.window);
      consumed.values().removeIf(t -> t.isBefore(earliest));
      reported.values().removeIf(t -> t.isBefore(earliest));
    }
  }

  public void finish() {
    advance(null, true);
  }

  public List<LeafNode> getLeaves() {
    return List.copyOf(leaves);
  }

  public List<Event> getRetainedEvents() {
    return leaves.stream()
        .flatMap(n -> n.getPartialMatches().stream())
        .flatMap(c -> c.binding.allEvents().stream())
        .distinct()
        .sorted(Comparator.comparing((Event e) -> e.timestamp).thenComparingLong(e -> e.index))
        .toList();
  }

  public String getStructureSummary() {
    return root.getStructureSummary();
  }

  public int getNodeCount() {
    return nodes.size();
  }

  public int getStoredCandidateCount() {
    return nodes.stream().mapToInt(n -> n.getPartialMatches().size()).sum();
  }
}
