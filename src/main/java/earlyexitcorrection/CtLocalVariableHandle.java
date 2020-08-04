package earlyexitcorrection;

import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.util.Iterator;
import java.util.List;

class CtLocalVariableHandle {
    static boolean handleLocalVariable(Factory factory,
                                       Iterator<CtCFlowBreak> returnsIterator,
                                       CtLocalVariable<?> variable,
                                       CtExpression<?> returnElement,
                                       boolean afterCycleReturn,
                                       CtBlock<?> newBody,
                                       CtStatement currentStatement) {

        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) currentStatement;
        CtExpression<?> assignment = localVariable.getAssignment();
        if (assignment instanceof CtBinaryOperator) {   //  TODO add possibility of multiple binary operators.
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) assignment;
            String leftHandOperand = binaryOperator.getLeftHandOperand().toString();
            String rightHandOperand = binaryOperator.getRightHandOperand().toString();

            List<CtExpression<Boolean>> newLeftOperator = EarlyExitVulnerabilityCorrection.getProtectionOfVariableOrEmpty(leftHandOperand);
            List<CtExpression<Boolean>> newRightOperator = EarlyExitVulnerabilityCorrection.getProtectionOfVariableOrEmpty(rightHandOperand);

            if (!newLeftOperator.isEmpty() && !newRightOperator.isEmpty() && !newLeftOperator.equals(newRightOperator)) {
                CtExpression<Boolean> newLeftCondition = getNewConditionOperator(factory, newLeftOperator);
                CtExpression<Boolean> newRightCondition = getNewConditionOperator(factory, newRightOperator);

                CtBinaryOperator<Boolean> newCondition = factory.createBinaryOperator(newLeftCondition, newRightCondition, BinaryOperatorKind.AND);
                protectLocalVariable(factory, newBody, localVariable, newCondition);
            } else if (!newLeftOperator.isEmpty()) {
                CtExpression<Boolean> newLeftCondition = getNewConditionOperator(factory, newLeftOperator);
                protectLocalVariable(factory, newBody, localVariable, newLeftCondition);
            } else if (!newRightOperator.isEmpty()) {
                CtExpression<Boolean> newRightCondition = getNewConditionOperator(factory, newRightOperator);
                protectLocalVariable(factory, newBody, localVariable, newRightCondition);
            } else {
                newBody.addStatement(currentStatement.clone());
            }
        } else {
            newBody.addStatement(currentStatement.clone());
        }
        return false;
    }

    private static CtExpression<Boolean> getNewConditionOperator(Factory factory, List<CtExpression<Boolean>> newOperator) {
        CtExpression<Boolean> newCondition = null;

        if (newOperator.size() > 1) {
            for (int i = 0; i < newOperator.size() - 1; i++) {
                if (newCondition == null) {
                    CtExpression<Boolean> left = negateCondition(factory, newOperator.get(i));
                    CtExpression<Boolean> right = negateCondition(factory, newOperator.get(++i));
                    newCondition = factory.createBinaryOperator(left, right, BinaryOperatorKind.AND);
                } else {
                    newCondition = factory.createBinaryOperator(newCondition, negateCondition(factory, newOperator.get(i)), BinaryOperatorKind.AND);
                }
            }
        } else {
            newCondition = negateCondition(factory, newOperator.get(0));
        }
        return newCondition;
    }

    private static CtUnaryOperator<Boolean> negateCondition(Factory factory, CtExpression<Boolean> temp) {
        CtUnaryOperator<Boolean> unaryOperator = factory.createUnaryOperator();
        unaryOperator.setKind(UnaryOperatorKind.NOT);
        unaryOperator.setOperand(temp);
        return unaryOperator;
    }

    private static void protectLocalVariable(Factory factory, CtBlock<?> newBody, CtLocalVariable<?> localVariable, CtExpression<Boolean> condition) {
        CtLocalVariable<?> newLocalVariable = localVariable.clone();
        CtTypeReference declaringType = newLocalVariable.getType();
        CtExpression oldAssignment = localVariable.getAssignment();

        if (declaringType.isPrimitive()) {
            CtExpression assignment;
            switch (declaringType.getSimpleName()) {
                case "String":
                    assignment = factory.createLiteral("");
                    break;
                case "boolean":
                    assignment = factory.createLiteral(false);
                    break;
                default :
                    assignment = factory.createLiteral(0);
                    break;
            }

            newLocalVariable.setAssignment(assignment);
            newBody.addStatement(newLocalVariable);
        }

        CtAssignment<?, ?> variableAssignment = factory.createVariableAssignment(newLocalVariable.getReference(), false, oldAssignment);
        variableAssignment.setType(declaringType);

        CtBlock<?> newThenBlock = factory.createBlock();
        newThenBlock.addStatement(variableAssignment);

        CtIf newIf = factory.createIf();
        newIf.setCondition(condition);
        newIf.setThenStatement(newThenBlock);

        newBody.addStatement(newIf);
    }
}