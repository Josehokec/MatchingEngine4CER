package corecer.parser.visitors;

import corecer.parser.COREBaseVisitor;
import corecer.parser.COREParser;
import corecer.parser.exceptions.ParserException;
import corecer.parser.exceptions.UnknownNameException;
import corecer.parser.plan.Event;
import corecer.parser.plan.Stream;
import corecer.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

public class FirstPassVisitor extends COREBaseVisitor<Void> {
    private final Set<Stream> streams;
    private final Set<Event> events;
    private int atomicPredicateCount;

    public FirstPassVisitor(){
        streams = new HashSet<>();
        events = new HashSet<>();
        atomicPredicateCount = 0;
    }

    public Set<Event> getEvents() {
        return events;
    }

    public Set<Stream> getStreams() {
        return streams;
    }

    public int getAtomicPredicateCount() {
        return atomicPredicateCount;
    }

    /**
     * Visits the given Context, adding its {@link Event} and {@link Stream}
     * (if any) to the Visitor.
     * @param ctx {@link COREParser.S_event_nameContext} specifying an Event.
     * @throws ParserException
     */
    @Override
    public Void visitS_event_name(COREParser.S_event_nameContext ctx) throws ParserException {
        if (ctx.stream_name() != null) {
            String streamName = StringUtils.tryRemoveQuotes(ctx.stream_name().getText());
            Stream stream = Stream.getSchemaFor(streamName);
            if (stream == null)
                throw new UnknownNameException("Stream `" + streamName + "` is not defined", ctx.stream_name());
            streams.add(stream);
        }

        String eventName = StringUtils.tryRemoveQuotes(ctx.event_name().getText());
        Event event = Event.getSchemaFor(eventName);
        if (event == null)
            throw new UnknownNameException("event `" + eventName + "` is not defined", ctx.event_name());

        // Add the newly found schema to the set of visited schemas
        events.add(event);
        return null;
    }

    @Override
    public Void visitEvent_filter(COREParser.Event_filterContext ctx) {
        atomicPredicateCount++;
        return super.visitEvent_filter(ctx);
    }
}
