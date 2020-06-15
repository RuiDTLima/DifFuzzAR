package controlflowcorrection;

import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.factory.Factory;

class CtBinaryOperatorModification {
    static String modifyBinaryOperator(Factory factory, CtBinaryOperator<?> binaryOperator) {
        CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
        CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();
        if (leftHandOperand instanceof CtVariableRead) {
            CtExpression<?> leftHandOperator = modifyVariableRead(factory, leftHandOperand);
            binaryOperator.setLeftHandOperand(leftHandOperator);
        } else if (leftHandOperand instanceof CtArrayRead) {
            CtArrayRead<?> leftOperand = CtArrayModification.modifyArrayOperation(factory, (CtArrayRead<?>) leftHandOperand);
            binaryOperator.setLeftHandOperand(leftOperand);
        }
        if (rightHandOperand instanceof CtVariableRead) {
            CtExpression<?> rightHandOperator = modifyVariableRead(factory, rightHandOperand);
            binaryOperator.setRightHandOperand(rightHandOperator);
        }
        return binaryOperator.toString();
    }

    private static CtExpression<?> modifyVariableRead(Factory factory, CtExpression<?> handOperand) {
        String leftHandOperator = handOperand.toString();
        CtExpression<?> newHandOperator;

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(leftHandOperator)) {
            String newLeftHandOperatorVariable = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(leftHandOperator);
            newHandOperator = factory.createCodeSnippetExpression(newLeftHandOperatorVariable);
        } else {
            String type = handOperand.getType().toString();
            int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
            newHandOperator = factory.createCodeSnippetExpression(ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(leftHandOperator, newHandOperator.toString());
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newHandOperator.toString(), type);
        }
        return  newHandOperator;
    }
}
