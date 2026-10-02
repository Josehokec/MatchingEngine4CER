package opencep.condition;

import java.util.function.BiConsumer;
import opencep.base.Binding;

public abstract class AtomicCondition implements Condition {
  private BiConsumer<AtomicCondition, Boolean> observer;

  protected abstract boolean test(Binding binding);

  public Result evaluate(Binding binding) {
    if (!binding.names().containsAll(getEventNames())) return Result.UNKNOWN;
    boolean value = test(binding);
    if (observer != null) observer.accept(this, value);
    return value ? Result.TRUE : Result.FALSE;
  }

  public void setStatisticsCollector(BiConsumer<AtomicCondition, Boolean> collector) {
    observer = collector;
  }

  public boolean isConditionOf(java.util.Set<String> names) {
    return names.containsAll(getEventNames());
  }
}
