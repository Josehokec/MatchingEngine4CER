package corecer.runtime.predicates;

import corecer.parser.plan.values.Attribute;
import corecer.parser.plan.values.ValueType;
import corecer.runtime.events.Event;
import corecer.util.Pair;

import java.util.Collection;
import java.util.List;

public class AttributeEvaluator extends ValueEvaluator {

    private final int[] eventToIdxArray;

    public AttributeEvaluator(Attribute attribute){

//        Set<corecer.parser.plan.Event> possibleEvents = attribute.getLabel().getEvents();
        Collection<corecer.parser.plan.Event> possibleEvents = corecer.parser.plan.Event.getAllEvents().values();
        eventToIdxArray = new int[corecer.parser.plan.Event.count()];

        possibleEvents.forEach(event -> {
            List<Pair<String, ValueType>> attributes = event.getAttributes();
            eventToIdxArray[event.getEventType()] = -1;
            for (int idx = 0; idx < attributes.size(); idx++) {
                if (attributes.get(idx).getKey().equals(attribute.getName())){
                    eventToIdxArray[event.getEventType()] = idx;
                    break;
                }
            }
        });
    }

    @Override
    public Object eval(Event event){
        int idx = eventToIdxArray[event.getType()];
        if (idx == -1) {
            return null;
        }
        return event.getValue(eventToIdxArray[event.getType()]);
    }
}
