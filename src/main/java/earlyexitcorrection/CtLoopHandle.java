package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import java.util.Set;

class CtLoopHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtLoopHandle.class);

    /**
     * The method where a loop is updated so that its body is transferred to be part of a 'then' block of a new 'if'
     * statement that will be the 'for' statement.
     * @param factory           The factory used to create new instructions.
     * @param loop              The loop to be updated.
     * @param stoppingCondition The stopping of the loop.
     * @return                  Returns a new loop with all changes implemented.
     */
    static CtLoop updateLoop(Factory factory, CtLoop loop, CtExpression<Boolean> stoppingCondition) {
        logger.info("The loop is being updated.");
        boolean modified = false;
        CtLoop newLoop = loop.clone();
        if (stoppingCondition instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) stoppingCondition;
            CtExpression<?> newRightHandOperand = null;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

            CtBinaryOperator<?> clone = binaryOperator.clone();

            if (EarlyExitVulnerabilityCorrection.isKeyInProtectedVariables(rightHandOperand.toString())) {
                CtExpression<?> otherVariable = obtainCompared(rightHandOperand);
                newRightHandOperand = clone.setRightHandOperand(otherVariable);
                modified = true;
            } else if (EarlyExitVulnerabilityCorrection.isKeyInProtectedVariables(leftHandOperand.toString())){
                CtExpression<?> otherVariable = obtainCompared(leftHandOperand);
                newRightHandOperand = clone.setLeftHandOperand(otherVariable);
                modified = true;
            }

            if (modified) {
                CtBinaryOperator<Boolean> newCondition = factory.createBinaryOperator();
                newCondition.setKind(BinaryOperatorKind.AND);
                newCondition.setLeftHandOperand(binaryOperator.clone());
                newCondition.setRightHandOperand(newRightHandOperand);
                CtIf newIf = factory.createIf();
                newIf.setCondition(newCondition);
                newIf.setThenStatement(loop.getBody());
                newLoop.setBody(newIf);
            }
        }
        return newLoop;
    }

    /**
     * The method where is obtained the expression to which 'handOperand' was compared to.
     * @param handOperand   The expression to obtain a previous comparison.
     * @return              Returns the expression to which 'handOperand' was compared previously.
     */
    private static CtExpression<?> obtainCompared(CtExpression<?> handOperand) {
        logger.info("Retrieving the variable used as a comparison with {}.", handOperand.toString());
        Set<CtExpression<Boolean>> conditionsList = EarlyExitVulnerabilityCorrection.getProtectionOfVariable(handOperand.toString());
        CtExpression<?> otherVariable = null;
        for (CtExpression<Boolean> expression : conditionsList) {
            CtBinaryOperator<Boolean> booleanCtExpression = (CtBinaryOperator<Boolean>) expression;
            CtExpression<?> leftHandOperand = booleanCtExpression.getLeftHandOperand();
            CtExpression<?> rightHandOperand = booleanCtExpression.getRightHandOperand();
            if (leftHandOperand.equals(handOperand)) {
                otherVariable = rightHandOperand;
            } else {
                otherVariable = leftHandOperand;
            }
        }
        return otherVariable;
    }
}