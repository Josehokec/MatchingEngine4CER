package corecer.runtime.predicates;

import corecer.parser.plan.values.Literal;
import corecer.runtime.events.Event;

public class LiteralEvaluator extends ValueEvaluator {

    private final Object value;

    public LiteralEvaluator(Literal literal){
        value = literal.getValue();
    }

    @Override
    public Object eval(Event event){
        return value;
    }
}
