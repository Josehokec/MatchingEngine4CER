package corecer.parser.plan.cea;

import corecer.parser.plan.Label;

public class AssignCEA extends CEA {
    public AssignCEA(CEA cea, Label label) {
        super(cea);
        for (Transition transition : transitions) {
            transition.addLabel(label);
        }
        labelSet.add(label);
    }
}
