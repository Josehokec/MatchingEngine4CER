package corecer.parser.plan.values.operations;


import corecer.parser.plan.exceptions.IncompatibleValueException;
import corecer.parser.plan.values.Value;
import corecer.parser.plan.values.ValueType;

public class Negation extends UnaryOperation {

    public Negation(Value inner) throws IncompatibleValueException {
        super(inner);
        // negation is only valid over numeric types
        if (!interoperableWith(ValueType.NUMERIC)) {
            throw new IncompatibleValueException();
        }
    }


    @Override
    public String toString() {
        return "(-" + inner.toString() + ")";
    }
}
