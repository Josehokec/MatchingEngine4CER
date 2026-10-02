package opencep.tree;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import opencep.condition.*;
import opencep.misc.Utils;

/** Storage supports binary-search range retrieval when a join key is available. */
public class PatternMatchStorage {
  protected final ArrayList<Candidate> values = new ArrayList<>();
  private final Function<Candidate, Object> key;
  private final boolean sorted;
  private final int cleanupInterval;
  private int additions;

  public PatternMatchStorage(TreeStorageParameters params, Function<Candidate, Object> key) {
    this.key = key;
    sorted = params.sortStorage() && key != null;
    cleanupInterval = params.cleanUpInterval();
  }

  public void add(Candidate candidate) {
    if (sorted)
      values.add(
          Utils.upperBound(
              values, candidate, Comparator.comparing(key, BaseRelationCondition::compareValues)),
          candidate);
    else values.add(candidate);
    additions++;
  }

  public List<Candidate> all() {
    return List.copyOf(values);
  }

  public int size() {
    return values.size();
  }

  public List<Candidate> get(Object target, RelopTypes relation) {
    if (!sorted || target == null) return all();
    int lower = bound(target, false), upper = bound(target, true);
    return switch (relation) {
      case EQUAL -> List.copyOf(values.subList(lower, upper));
      case NOT_EQUAL -> {
        var out = new ArrayList<>(values.subList(0, lower));
        out.addAll(values.subList(upper, values.size()));
        yield out;
      }
      case SMALLER -> List.copyOf(values.subList(0, lower));
      case SMALLER_EQUAL -> List.copyOf(values.subList(0, upper));
      case GREATER -> List.copyOf(values.subList(upper, values.size()));
      case GREATER_EQUAL -> List.copyOf(values.subList(lower, values.size()));
    };
  }

  private int bound(Object target, boolean upper) {
    int lo = 0, hi = values.size();
    while (lo < hi) {
      int m = (lo + hi) >>> 1;
      int cmp = BaseRelationCondition.compareValues(key.apply(values.get(m)), target);
      if (cmp < 0 || (upper && cmp == 0)) lo = m + 1;
      else hi = m;
    }
    return lo;
  }

  public void cleanExpired(Instant earliest, boolean force) {
    if (force || additions >= cleanupInterval) {
      values.removeIf(c -> c.first.isBefore(earliest));
      additions = 0;
    }
  }
}
