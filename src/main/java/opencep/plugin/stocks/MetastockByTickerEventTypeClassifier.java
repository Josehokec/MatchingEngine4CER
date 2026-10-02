package opencep.plugin.stocks;

import java.util.Map;
import opencep.base.EventTypeClassifier;

public final class MetastockByTickerEventTypeClassifier implements EventTypeClassifier {
  public String getEventType(Map<String, Object> payload) {
    return payload.get("Stock Ticker").toString();
  }
}
