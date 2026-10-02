package opencep.parallel;

import java.util.Set;
import opencep.base.*;

public interface DataParallelExecutionAlgorithm {
  Set<Integer> classify(Event event);

  boolean isOwner(int unit, PatternMatch match);

  int units();
}
