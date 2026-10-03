package corecer.parser.visitors;

import corecer.parser.COREBaseVisitor;
import corecer.parser.COREParser;
import corecer.parser.exceptions.UnknownNameException;
import corecer.parser.plan.Label;
import corecer.parser.plan.exceptions.NoSuchLabelException;
import corecer.parser.plan.filter.AndFilter;
import corecer.parser.plan.filter.AtomicFilter;
import corecer.parser.plan.filter.Filter;
import corecer.parser.plan.filter.OrFilter;
import corecer.parser.plan.predicate.AtomicPredicate;
import corecer.util.StringUtils;

public class FilterVisitor extends COREBaseVisitor<Filter> {

    @Override
    public Filter visitPar_filter(COREParser.Par_filterContext ctx) {
        return ctx.filter().accept(this);
    }

    @Override
    public Filter visitAnd_filter(COREParser.And_filterContext ctx) {
        Filter left = ctx.filter(0).accept(this);
        Filter right = ctx.filter(1).accept(this);
        return new AndFilter(left, right);
    }

    @Override
    public Filter visitEvent_filter(COREParser.Event_filterContext ctx) {
        String labelName = StringUtils.tryRemoveQuotes(ctx.event_name().getText());
        Label label;
        try {
            label = Label.get(labelName);
        } catch (NoSuchLabelException exc) {
            throw new UnknownNameException("event or label '" + labelName + "' was never declared", ctx);
        }
        AtomicPredicate predicate = new PredicateVisitor(label).visit(ctx.bool_expr());
        return new AtomicFilter(label, predicate);
    }

    @Override
    public Filter visitOr_filter(COREParser.Or_filterContext ctx) {
        Filter left = ctx.filter(0).accept(this);
        Filter right = ctx.filter(1).accept(this);
        return new OrFilter(left, right);
    }

}
