package corecer.parser.plan.values.operations;


import corecer.parser.plan.exceptions.IncompatibleValueException;
import corecer.parser.plan.values.Value;
import corecer.parser.plan.values.ValueType;

public class Addition extends BinaryOperation {

    public Addition(Value lhs, Value rhs) throws IncompatibleValueException {
        super(lhs, rhs);
        // additions is compatible with strings and numbers
        if (!interoperableWith(ValueType.NUMERIC) && !interoperableWith(ValueType.STRING)) {
            throw new IncompatibleValueException();
        }
    }

    @Override
    public String toString() {
        return "(" + lhs.toString() + " + " + rhs.toString() + ")";
    }
}
