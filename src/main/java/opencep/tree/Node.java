package opencep.tree;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import opencep.base.*;
import opencep.misc.SelectionStrategies;
import opencep.plan.TreePlanNode;

public abstract class Node {
  protected final boolean legacy;
  protected static final ThreadLocal<Boolean> SUPPRESS_EXPIRATION =
      ThreadLocal.withInitial(() -> false);
  public final TreePlanNode plan;
  protected final Pattern pattern;
  protected opencep.condition.Condition condition;
  protected final PatternMatchStorage storage;
  protected java.time.Duration retentionWindow;
  private final List<Consumer<Candidate>> parents = new ArrayList<>();
  private final Map<String, Instant> seen = new HashMap<>();
  private final Map<Long, Instant> consumed = new HashMap<>();

  protected Node(TreePlanNode plan, Pattern pattern, TreeStorageParameters params) {
    this.legacy = params.legacySemantics();
    this.plan = plan;
    this.pattern = pattern;
    this.condition = new opencep.condition.TrueCondition();
    this.retentionWindow = pattern.window;
    storage = new PatternMatchStorage(params, params.sortStorage() ? c -> c.first : null);
  }

  public void ensureRetentionWindow(java.time.Duration window) {
    if (window.compareTo(retentionWindow) > 0) retentionWindow = window;
  }

  public void setCondition(opencep.condition.Condition value) {
    condition = value;
  }

  public void addParent(Consumer<Candidate> parent) {
    parents.add(parent);
  }

  public List<Candidate> getPartialMatches() {
    return storage.all();
  }

  public Set<String> getEventNames() {
    return plan.getEventNames();
  }

  protected void emit(Candidate c) {
    if (c == null
        || !StructureConstraints.inWindow(c, pattern.window)
        || !condition.mayMatch(c.binding)
        || (!legacy && !StructureConstraints.contiguous(pattern, c.binding))) return;
    if (c.probability != null) {
      if (pattern.confidence == null)
        throw new IllegalArgumentException(
            "A probabilistic pattern requires a confidence threshold");
      if (c.probability < pattern.confidence) return;
    }
    var policy = pattern.consumptionPolicy;
    boolean next = policy.strategy == SelectionStrategies.MATCH_NEXT && !(this instanceof LeafNode);
    if (next
        && c.binding.allEvents().stream()
            .anyMatch(e -> policy.appliesTo(e.type) && consumed.containsKey(e.index))) return;
    if ((!legacy || this instanceof LeafNode) && seen.putIfAbsent(c.binding.key(), c.first) != null)
      return;
    if (next)
      c.binding.allEvents().stream()
          .filter(e -> policy.appliesTo(e.type))
          .forEach(e -> consumed.put(e.index, e.timestamp));
    storage.add(c);
    for (var parent : List.copyOf(parents)) parent.accept(c);
  }

  public void advance(Instant timestamp, boolean end) {
    if (timestamp == null || (legacy && SUPPRESS_EXPIRATION.get())) return;
    var earliest = timestamp.minus(legacy ? pattern.window : retentionWindow);
    storage.cleanExpired(earliest, end);
    seen.values().removeIf(t -> t.isBefore(earliest));
    consumed.values().removeIf(t -> t.isBefore(earliest));
  }

  public String getStructureSummary() {
    return plan.getStructureSummary();
  }
}
