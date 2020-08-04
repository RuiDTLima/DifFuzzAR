package earlyexitcorrection;

import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import java.util.List;

class CtLoopHandle {
    //  TODO check stopping condition and body.
    static CtLoop updateLoop(Factory factory, CtLoop loop, CtExpression<Boolean> stoppingCondition) {
        boolean modified = false;
        CtLoop newLoop = loop.clone();
        if (stoppingCondition instanceof CtBinaryOperator) {    //  TODO check for multiple binaryOperators.
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) stoppingCondition;
            CtBinaryOperator<Boolean> newStoppingCondition;
            CtExpression<?> newRightHandOperand = null;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

            CtBinaryOperator<?> clone = binaryOperator.clone();

            if (EarlyExitVulnerabilityCorrection.isKeyInProtectedVariables(rightHandOperand.toString())) {
                CtExpression<?> otherVariable = modifyHandOperand(rightHandOperand);
                newRightHandOperand = clone.setRightHandOperand(otherVariable);
                modified = true;
            } else if (EarlyExitVulnerabilityCorrection.isKeyInProtectedVariables(leftHandOperand.toString())){
                CtExpression<?> otherVariable = modifyHandOperand(leftHandOperand);
                newRightHandOperand = clone.setLeftHandOperand(otherVariable);
                modified = true;
            }

            if (modified) {
                newStoppingCondition = factory.createBinaryOperator();
                newStoppingCondition.setKind(BinaryOperatorKind.AND);
                newStoppingCondition.setLeftHandOperand(binaryOperator.clone());
                newStoppingCondition.setRightHandOperand(newRightHandOperand);
                CtIf newIf = factory.createIf();
                newIf.setCondition(newStoppingCondition);
                newIf.setThenStatement(loop.getBody());
                newLoop.setBody(newIf);
            }
        }
        return newLoop;
    }

    private static CtExpression<?> modifyHandOperand(CtExpression<?> handOperand) {
        List<CtExpression<Boolean>> conditionsList = EarlyExitVulnerabilityCorrection.getProtectionOfVariable(handOperand.toString());
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
