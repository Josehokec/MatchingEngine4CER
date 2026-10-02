package opencep.tree;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import opencep.condition.*;

/** A per-parent range index, allowing a shared child to serve different join keys. */
final class JoinIndex {
  private final Function<Candidate, Object> key;
  private final NavigableMap<Object, List<Candidate>> entries =
      new TreeMap<>(BaseRelationCondition::compareValues);

  JoinIndex(Function<Candidate, Object> key) {
    this.key = key;
  }

  void add(Candidate candidate) {
    Object value = key.apply(candidate);
    if (value != null) entries.computeIfAbsent(value, ignored -> new ArrayList<>()).add(candidate);
  }

  List<Candidate> get(Object target, RelopTypes relation) {
    if (target == null) return List.of();
    Collection<List<Candidate>> selected =
        switch (relation) {
          case EQUAL -> Collections.singletonList(entries.getOrDefault(target, List.of()));
          case SMALLER -> entries.headMap(target, false).values();
          case SMALLER_EQUAL -> entries.headMap(target, true).values();
          case GREATER -> entries.tailMap(target, false).values();
          case GREATER_EQUAL -> entries.tailMap(target, true).values();
          case NOT_EQUAL -> {
            var values = new ArrayList<>(entries.headMap(target, false).values());
            values.addAll(entries.tailMap(target, false).values());
            yield values;
          }
        };
    return selected.stream().flatMap(Collection::stream).toList();
  }

  void cleanExpired(Instant earliest) {
    entries
        .values()
        .forEach(values -> values.removeIf(candidate -> candidate.first.isBefore(earliest)));
    entries.values().removeIf(List::isEmpty);
  }
}
