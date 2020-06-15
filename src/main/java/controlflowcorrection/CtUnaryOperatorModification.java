package controlflowcorrection;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.factory.Factory;

class CtUnaryOperatorModification {
    static CtStatement modifyUnaryOperator(Factory factory, CtUnaryOperator<?> unaryOperator) {
        CtStatement statement;
        String operand = unaryOperator.getOperand().toString();

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(operand)) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(operand);
            CtExpression variableExpression = factory.createCodeSnippetExpression(valueVariablesReplacement);
            statement = unaryOperator.setOperand(variableExpression);
        } else {
            statement = null;
        }
        return statement;
    }
}
