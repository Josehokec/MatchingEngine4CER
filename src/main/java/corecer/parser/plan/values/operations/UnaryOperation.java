package corecer.parser.plan.values.operations;


import corecer.parser.plan.values.Value;

public abstract class UnaryOperation extends Operation {

    Value inner;

    UnaryOperation(Value inner) {
        super(inner);
        this.inner = inner;
    }

    public Value getInner() {
        return inner;
    }
}
