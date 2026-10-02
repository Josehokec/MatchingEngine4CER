package opencep.adaptive;

import java.time.Duration;
import opencep.base.*;

public final class StatisticsCollector {
  public final ArrivalRatesStatistics arrivalRates;
  public final SelectivityStatistics selectivity;

  public StatisticsCollector(Pattern pattern, Duration window) {
    arrivalRates = new ArrivalRatesStatistics(pattern, window);
    selectivity = new SelectivityStatistics(pattern);
    pattern
        .condition
        .extractAtomicConditions()
        .forEach(c -> c.setStatisticsCollector(selectivity::update));
  }

  public void handleEvent(Event event) {
    arrivalRates.update(event);
  }

  public StatisticsSnapshot getStatistics() {
    return new StatisticsSnapshot(arrivalRates.getStatistics(), selectivity.getStatistics());
  }

  public void detach(Pattern pattern) {
    pattern.condition.extractAtomicConditions().forEach(c -> c.setStatisticsCollector(null));
  }
}
