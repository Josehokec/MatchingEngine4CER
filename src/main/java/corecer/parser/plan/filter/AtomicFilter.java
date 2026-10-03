package corecer.parser.plan.filter;

import corecer.parser.plan.Label;
import corecer.parser.plan.cea.CEA;
import corecer.parser.plan.cea.PredicateFactory;
import corecer.parser.plan.predicate.AtomicPredicate;

public class AtomicFilter extends Filter {
    private Label label;
    private AtomicPredicate predicate;


    public AtomicFilter(Label label, AtomicPredicate predicate){
        this.label = label;
        this.predicate = predicate;
    }

    public Label getLabel() {
        return label;
    }

    public AtomicPredicate getPredicate() {
        return predicate;
    }

    @Override
    public CEA applyToCEA(CEA cea) {
        return cea.addPredicate(PredicateFactory.getInstance().from(predicate), label);
    }
}
