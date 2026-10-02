package cersrt;

import java.util.Map;

/** A pure transition predicate, evaluated before the transition writes its register. */
@FunctionalInterface
public interface Guard {
  boolean test(Event event, Map<String, Event> registers);
}
