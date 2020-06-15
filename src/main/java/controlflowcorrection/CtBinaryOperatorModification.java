package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.factory.Factory;

class CtBinaryOperatorModification {
    private static final Logger logger = LoggerFactory.getLogger(CtBinaryOperatorModification.class);

    static String modifyBinaryOperator(Factory factory, CtBinaryOperator<?> binaryOperator) {
        logger.info("Modifying a binary operator.");
        CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
        CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

        if (leftHandOperand instanceof CtVariableRead) {
            CtExpression<?> leftHandOperator = modifyVariableRead(factory, leftHandOperand);
            binaryOperator.setLeftHandOperand(leftHandOperator);
            logger.info("The left hand operand is a variable read.");
        } else if (leftHandOperand instanceof CtArrayRead) {
            CtArrayRead<?> leftOperand = CtArrayModification.modifyArrayOperation(factory, (CtArrayRead<?>) leftHandOperand);
            binaryOperator.setLeftHandOperand(leftOperand);
            logger.info("The left hand operand is an array read.");
        }

        if (rightHandOperand instanceof CtVariableRead) {
            CtExpression<?> rightHandOperator = modifyVariableRead(factory, rightHandOperand);
            binaryOperator.setRightHandOperand(rightHandOperator);
            logger.info("The right hand operand is a variable read.");
        }

        return binaryOperator.toString();
    }

    private static CtExpression<?> modifyVariableRead(Factory factory, CtExpression<?> handOperand) {
        String handOperator = handOperand.toString();
        CtExpression<?> newHandOperator;

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(handOperator)) {
            String newHandOperatorVariable = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(handOperator);
            newHandOperator = factory.createCodeSnippetExpression(newHandOperatorVariable);
            logger.info("The hand operand is a variable that already was replaced.");
        } else {
            String type = handOperand.getType().toString();
            int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
            newHandOperator = factory.createCodeSnippetExpression(ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(handOperator, newHandOperator.toString());
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newHandOperator.toString(), type);
            logger.info("The hand operand is to a variable that will now be replaced.");
        }
        return  newHandOperator;
    }
}
