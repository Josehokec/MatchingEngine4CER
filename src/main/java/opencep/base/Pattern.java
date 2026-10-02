package opencep.base;

import java.time.Duration;
import java.util.*;
import opencep.adaptive.StatisticsSnapshot;
import opencep.condition.*;
import opencep.misc.*;

public final class Pattern {
  public final PatternStructure fullStructure;
  public final Condition condition;
  public final Duration window;
  public final ConsumptionPolicy consumptionPolicy;
  public final Integer id;
  public final Double confidence;
  private StatisticsSnapshot statistics;

  public Pattern(PatternStructure structure, Condition condition, Duration window) {
    this(structure, condition, window, new ConsumptionPolicy(), null, null);
  }

  public Pattern(
      PatternStructure structure,
      Condition condition,
      Duration window,
      ConsumptionPolicy policy,
      Integer id,
      Double confidence) {
    this.fullStructure = Objects.requireNonNull(structure);
    this.condition = condition == null ? new TrueCondition() : condition;
    this.window = Objects.requireNonNull(window);
    if (window.isNegative()) throw new IllegalArgumentException("Negative time window");
    if (confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1))
      throw new IllegalArgumentException("Invalid confidence");
    this.consumptionPolicy = policy == null ? new ConsumptionPolicy() : policy;
    this.id = id;
    this.confidence = confidence;
    if (positiveNames(structure).isEmpty())
      throw new IllegalArgumentException("A pattern needs a positive event");
    validateAliases(structure);
    if (!new HashSet<>(structure.getAllEventNames()).containsAll(this.condition.getEventNames()))
      throw new IllegalArgumentException("Condition references unknown aliases");
    for (var c : consumptionPolicy.contiguousNames) {
      if (c.size() < 2 || !extractFlatSequences().stream().anyMatch(s -> containsContiguous(s, c)))
        throw new IllegalArgumentException("Contiguity must follow a flat SEQ");
    }
    if (!new HashSet<>(structure.getAllEventNames()).containsAll(consumptionPolicy.freezeNames))
      throw new IllegalArgumentException("Unknown freeze alias");
  }

  private static void validateAliases(PatternStructure s) {
    if (s instanceof CompositeStructure c) {
      c.args.forEach(Pattern::validateAliases);
      if (!(s instanceof OrOperator)) {
        var seen = new HashSet<String>();
        for (var a : c.args)
          for (var n : new HashSet<>(a.getAllEventNames()))
            if (!seen.add(n)) throw new IllegalArgumentException("Duplicate alias: " + n);
      }
    } else if (s instanceof UnaryStructure u) validateAliases(u.arg);
  }

  private static boolean containsContiguous(List<String> list, List<String> sub) {
    for (int i = 0; i + sub.size() <= list.size(); i++)
      if (list.subList(i, i + sub.size()).equals(sub)) return true;
    return false;
  }

  public static Set<String> positiveNames(PatternStructure s) {
    if (s instanceof NegationOperator) return Set.of();
    if (s instanceof PrimitiveEventStructure p) return Set.of(p.name());
    if (s instanceof UnaryStructure u) return positiveNames(u.arg);
    var out = new LinkedHashSet<String>();
    ((CompositeStructure) s).args.forEach(a -> out.addAll(positiveNames(a)));
    return out;
  }

  public List<PrimitiveEventStructure> getPrimitiveEvents() {
    return fullStructure.getPrimitiveEvents();
  }

  public Set<String> getAllEventTypes() {
    return new HashSet<>(getPrimitiveEvents().stream().map(PrimitiveEventStructure::type).toList());
  }

  public List<String> getPrimitiveEventNames() {
    return fullStructure.getAllEventNames();
  }

  public int countPrimitiveEvents() {
    return getPrimitiveEvents().size();
  }

  public int getIndexByEventName(String name) {
    int i = getPrimitiveEventNames().indexOf(name);
    if (i < 0) throw new IllegalArgumentException("Unknown alias: " + name);
    return i;
  }

  public List<List<String>> extractFlatSequences() {
    var out = new ArrayList<List<String>>();
    extract(fullStructure, out);
    return out;
  }

  private static void extract(PatternStructure s, List<List<String>> out) {
    if (s instanceof SeqOperator c)
      out.add(
          c.args.stream()
              .filter(PrimitiveEventStructure.class::isInstance)
              .map(a -> ((PrimitiveEventStructure) a).name())
              .toList());
    if (s instanceof CompositeStructure c) c.args.forEach(a -> extract(a, out));
    else if (s instanceof UnaryStructure u) extract(u.arg, out);
  }

  public void setStatistics(StatisticsSnapshot s) {
    if (s != null && s.size() != countPrimitiveEvents())
      throw new IllegalArgumentException("Statistics size mismatch");
    statistics = s;
  }

  public StatisticsSnapshot getStatistics() {
    return statistics;
  }

  public Pattern getSubPattern(Set<String> names) {
    var s = fullStructure.getStructureProjection(names);
    if (s == null) return null;
    var p =
        new Pattern(
            s,
            condition.getConditionProjection(names),
            window,
            new ConsumptionPolicy(),
            id,
            confidence);
    if (statistics != null)
      p.setStatistics(statistics.project(getPrimitiveEventNames(), p.getPrimitiveEventNames()));
    return p;
  }

  public boolean isSubPattern(Pattern other) {
    var p = other.getSubPattern(new HashSet<>(getPrimitiveEventNames()));
    return p != null && fullStructure.equals(p.fullStructure) && condition.equals(p.condition);
  }

  public String toString() {
    return fullStructure + " WHERE " + condition + " WITHIN " + window;
  }
}
