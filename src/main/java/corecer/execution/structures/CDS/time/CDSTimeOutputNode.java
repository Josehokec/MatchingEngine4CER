package corecer.execution.structures.CDS.time;

import corecer.parser.plan.cea.Transition;
import corecer.runtime.events.Event;

public class CDSTimeOutputNode extends CDSTimeNode {

    public static final CDSTimeNode BOTTOM = new CDSTimeOutputNode();

    private Event event;
    private Transition.TransitionType transitionType;
    private CDSTimeNode child;

    private CDSTimeOutputNode() {}

    CDSTimeOutputNode(CDSTimeNode child, Transition.TransitionType transitionType, Event event, long currentTime) {
        if (child == BOTTOM) {
            this.mm = currentTime;
        } else {
            this.mm = child.getMm();
        }
        this.child = child;
        this.transitionType = transitionType;
        this.event = event;
    }

    public Event getEvent() {
        return event;
    }

    public Transition.TransitionType getTransitionType() {
        return transitionType;
    }

    public boolean isBottom() {
        return this == BOTTOM;
    }

    public CDSTimeNode getChild() {
        return child;
    }
}
