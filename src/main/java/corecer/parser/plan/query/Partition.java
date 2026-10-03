package corecer.parser.plan.query;

import corecer.runtime.events.Event;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Partition {

    private Set<String> attributes;

    public Partition(Collection<String> attributes) {
        this.attributes = new HashSet<>(attributes);
        // TODO : check that partition attributes cover all events on query
    }

    public Set<String> getAttributes() {
        return attributes;
    }

    public String getAttributeFor(Event e){
        return attributes.iterator().next();
    }

}
