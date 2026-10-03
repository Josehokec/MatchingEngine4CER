package corecer.parser.plan;

import corecer.parser.plan.exceptions.NoSuchLabelException;
import corecer.parser.plan.values.ValueType;

import java.util.*;

public class Label {


    private static Map<String, Label> stringLabelMap = new HashMap<>();

    /** Reset between sequential sessions; schemas are process-global upstream. */
    public static void invalidateLabelsSchema() {
        stringLabelMap.clear();
        Event any = Event.ANY();
        forName(any.getName(), Collections.singleton(any));
    }

    private Set<Event> events;

    /**
     * Retrieves or creates the Label for the given name and adds to it
     * the events from the {@link Event} Set. Then returns the Label.
     *
     * @param name String specifying the Label's name.
     * @param eventSchemaSet Set of Events.
     * @return The Label with all Events added to it.
     */
    public static Label forName(String name, Set<Event> eventSchemaSet) {
        Label label = stringLabelMap.get(name);
        if (label == null) {
            label = new Label(name);
            stringLabelMap.put(name, label);
        }
        label.events.addAll(eventSchemaSet);
        return label;
    }

    private String name;

    private Label(String name) {
        this.name = name;
        this.events = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public static Label get(String name) throws NoSuchLabelException {
        Label label = stringLabelMap.get(name);
        if (label == null) {
            throw new NoSuchLabelException("No label defined for name " + name);
        }
        return label;
    }

    public Set<Event> getEvents() {
        return new HashSet<>(events);
    }

    public Map<String, EnumSet<ValueType>> getAttributes() {
        // returns all the attributes and their respective Values defined on events
        // within this label.

        Map<String, EnumSet<ValueType>> attributeValues = new HashMap<>();

        events.forEach(event -> {
            event.getAttributes().stream().forEach(pair ->{
                if (!attributeValues.containsKey(pair.getKey())){
                    attributeValues.put(pair.getKey(), EnumSet.noneOf(ValueType.class));
                }
                attributeValues.get(pair.getKey()).add(pair.getValue());
            });
        });

        return attributeValues;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Label) return name.equals(((Label) obj).name);
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
