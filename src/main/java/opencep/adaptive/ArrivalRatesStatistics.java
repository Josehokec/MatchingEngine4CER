package opencep.adaptive;

import java.time.*;
import java.util.*;
import opencep.base.*;

public final class ArrivalRatesStatistics {
  private final Duration window;
  private final List<PrimitiveEventStructure> definitions;
  private final ArrayDeque<Event> arrivals = new ArrayDeque<>();
  private final double[] rates;

  public ArrivalRatesStatistics(Pattern pattern, Duration window) {
    this.window = window;
    definitions = pattern.getPrimitiveEvents();
    rates =
        pattern.getStatistics() == null
            ? new double[definitions.size()]
            : pattern.getStatistics().arrivalRates();
  }

  private void change(Event event, int delta) {
    for (int i = 0; i < rates.length; i++)
      if (definitions.get(i).type().equals(event.type)) rates[i] += delta;
  }

  public void update(Event event) {
    if (definitions.stream().anyMatch(p -> p.type().equals(event.type))) {
      arrivals.addLast(event);
      change(event, 1);
    }
    while (!arrivals.isEmpty()
        && Duration.between(arrivals.peekFirst().timestamp, event.timestamp).compareTo(window) > 0)
      change(arrivals.removeFirst(), -1);
  }

  public double[] getStatistics() {
    return rates.clone();
  }
}
