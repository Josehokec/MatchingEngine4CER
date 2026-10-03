package corecer.execution.structures.CDS;

import corecer.parser.plan.cea.Transition;
import corecer.runtime.events.Event;

public class CDSOutputNode extends CDSNode {

    public static final CDSNode BOTTOM = new CDSOutputNode();

    private Event event;
    private Transition.TransitionType transitionType;
    private CDSNode child;

    private CDSOutputNode() {}

    public CDSOutputNode(CDSNode child, Transition.TransitionType transitionType, Event event) {
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

    public CDSNode getChild() {
        return child;
    }
}
