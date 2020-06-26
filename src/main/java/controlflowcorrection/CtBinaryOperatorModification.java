package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import util.NamingConvention;

class CtBinaryOperatorModification {
    private static final Logger logger = LoggerFactory.getLogger(CtBinaryOperatorModification.class);

    static CtBinaryOperator<Boolean> modifyBinaryOperator(Factory factory, CtBinaryOperator<Boolean> binaryOperator) {
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

        return binaryOperator;
    }

    private static CtExpression<?> modifyVariableRead(Factory factory, CtExpression<?> handOperand) {
        String handOperator = handOperand.toString();
        CtExpression<?> newHandOperator;

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(handOperator)) {
            String newHandOperatorVariable = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(handOperator);
            newHandOperator = factory.createCodeSnippetExpression(newHandOperatorVariable);
            logger.info("The hand operand is a variable that already was replaced.");
        } else {
            CtTypeReference<?> type = handOperand.getType();
            CtLocalVariable<?> localVariable = NamingConvention.produceNewVariable(factory, type, null);
            newHandOperator = factory.createVariableRead(localVariable.getReference(), false);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(handOperator, newHandOperator.toString());
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newHandOperator.toString(), type, null);
            logger.info("The hand operand is a variable that will now be replaced.");
        }
        return  newHandOperator;
    }

    static boolean equals(CtBinaryOperator<Boolean> firstCondition, CtBinaryOperator<Boolean> secondCondition) {
        BinaryOperatorKind firstConditionKind = firstCondition.getKind();
        BinaryOperatorKind secondConditionKind = secondCondition.getKind();

        CtExpression<?> firstConditionLeftHandOperand = firstCondition.getLeftHandOperand();
        CtExpression<?> firstConditionRightHandOperand = firstCondition.getRightHandOperand();

        CtExpression<?> secondConditionLeftHandOperand = secondCondition.getLeftHandOperand();
        CtExpression<?> secondConditionRightHandOperand = secondCondition.getRightHandOperand();

        boolean equals = firstConditionKind.equals(secondConditionKind);
        equals &= firstConditionLeftHandOperand.toString().equals(secondConditionLeftHandOperand.toString());
        equals &= firstConditionRightHandOperand.toString().equals(secondConditionRightHandOperand.toString());
        
        return equals;
    }
}
