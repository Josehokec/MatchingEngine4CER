package corecer.runtime.predicates;


import corecer.parser.plan.predicate.LikePredicate;
import corecer.runtime.events.Event;

public class LikeEvaluator  extends PredicateEvaluator {

    public LikeEvaluator(LikePredicate predicate){

    }

    @Override
    public boolean eval(Event event) {
        return false;
    }
}
