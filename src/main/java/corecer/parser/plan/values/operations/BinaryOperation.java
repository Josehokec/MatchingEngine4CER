package corecer.parser.plan.values.operations;


import corecer.parser.plan.values.Value;

public abstract class BinaryOperation extends Operation {

    Value lhs, rhs;

    BinaryOperation(Value lhs, Value rhs) {
        super(lhs, rhs);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public Value getRhs() {
        return rhs;
    }

    public Value getLhs() {
        return lhs;
    }
}
