package corecer.parser.visitors;


import corecer.parser.COREBaseVisitor;
import corecer.parser.COREParser;
import corecer.parser.exceptions.DuplicateNameException;
import corecer.parser.plan.Event;
import corecer.parser.plan.exceptions.EventException;
import corecer.parser.plan.values.ValueType;
import corecer.util.StringUtils;
import corecer.util.Pair;

import java.util.List;

public class EventDeclarationVisitor extends COREBaseVisitor<Event> {

    public Event visitEvent_declaration(COREParser.Event_declarationContext ctx) {
        String eventName = StringUtils.tryRemoveQuotes(ctx.event_name().getText());
        List<Pair<String, ValueType>> attributeMap = new AttributeDeclarationVisitor().visitAttribute_dec_list(ctx.attribute_dec_list());

        try {
            return new Event(eventName, attributeMap);
        } catch (EventException exc) {
            throw new DuplicateNameException(exc.getMessage(), ctx);
        }
    }
}

