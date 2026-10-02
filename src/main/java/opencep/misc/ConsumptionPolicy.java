package opencep.misc;

import java.util.*;

public final class ConsumptionPolicy {
  public final SelectionStrategies strategy;
  public final Set<String> singleTypes;
  public final List<List<String>> contiguousNames;
  public final Set<String> freezeNames;

  public ConsumptionPolicy() {
    this(SelectionStrategies.MATCH_ANY, null, List.of(), Set.of());
  }

  public ConsumptionPolicy(SelectionStrategies strategy) {
    this(strategy, null, List.of(), Set.of());
  }

  /** Null singleTypes applies strategy to all event types; empty applies it to none. */
  public ConsumptionPolicy(
      SelectionStrategies strategy,
      Set<String> singleTypes,
      List<List<String>> contiguous,
      Set<String> freeze) {
    this.strategy = Objects.requireNonNull(strategy);
    this.singleTypes = singleTypes == null ? null : Set.copyOf(singleTypes);
    contiguousNames = contiguous.stream().map(List::copyOf).toList();
    freezeNames = Set.copyOf(freeze);
  }

  public boolean appliesTo(String type) {
    return strategy != SelectionStrategies.MATCH_ANY
        && (singleTypes == null || singleTypes.contains(type));
  }

  public ConsumptionPolicy withContiguous(String... names) {
    return new ConsumptionPolicy(strategy, singleTypes, List.of(List.of(names)), freezeNames);
  }

  public ConsumptionPolicy withFreeze(String... names) {
    return new ConsumptionPolicy(strategy, singleTypes, contiguousNames, Set.of(names));
  }
}
