package corecer.parser.plan.values.operations;


import corecer.parser.plan.exceptions.IncompatibleValueException;
import corecer.parser.plan.values.Value;
import corecer.parser.plan.values.ValueType;

public class Subtraction extends BinaryOperation {

    public Subtraction(Value lhs, Value rhs) throws IncompatibleValueException {
        super(lhs, rhs);
        // subtraction is only valid over numeric types
        if (!interoperableWith(ValueType.NUMERIC)) {
            throw new IncompatibleValueException();
        }
    }

    @Override
    public String toString() {
        return "(" + lhs.toString() + " - " + rhs.toString() + ")";
    }
}
