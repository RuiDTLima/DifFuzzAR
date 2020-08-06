package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.util.Iterator;
import java.util.List;

class CtLocalVariableHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtLocalVariableHandle.class);

    /**
     * The method where a 'local variable' is handled. An attempt will be made to protect the local variable. If the
     * value being assigned is a binary operator then a condition will be added considering each hand of the assignment.
     * @param factory   The factory used to create new instructions.
     * @param returnsIterator   An iterator over the returns of the method. NOT USED.
     * @param returnVariable  The variable to be returned. NOT USED.
     * @param returnElement The valid return expression returned in the final return statement. Can't be a binary operator
     *                      nor an invocation that uses a variable. NOT USED.
     * @param afterWhileReturn  Indicates if this 'for' statement happens after a 'while' statement.
     * @param newBody   A block of statements that will become the new body of the vulnerable method.
     * @param currentStatement  The 'local variable' statement under analysis.
     * @return  Indicates if the next statement will be after a 'while' cycle.
     */
    static boolean handleLocalVariable(Factory factory,
                                       Iterator<CtCFlowBreak> returnsIterator,
                                       CtLocalVariable<?> returnVariable,
                                       CtExpression<?> returnElement,
                                       boolean afterWhileReturn,
                                       CtBlock<?> newBody,
                                       CtStatement currentStatement) {

        logger.info("Handling a 'local variable' statement.");
        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) currentStatement;
        CtExpression<?> assignment = localVariable.getAssignment();
        CtStatement newStatement = currentStatement.clone();
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
                newStatement = protectLocalVariable(factory, newBody, localVariable, newCondition);
            } else if (!newLeftOperator.isEmpty()) {
                CtExpression<Boolean> newLeftCondition = getNewConditionOperator(factory, newLeftOperator);
                newStatement = protectLocalVariable(factory, newBody, localVariable, newLeftCondition);
            } else if (!newRightOperator.isEmpty()) {
                CtExpression<Boolean> newRightCondition = getNewConditionOperator(factory, newRightOperator);
                newStatement = protectLocalVariable(factory, newBody, localVariable, newRightCondition);
            }
        }
        newBody.addStatement(newStatement);
        return false;
    }

    /**
     * The method where a new condition is produced considering the operators received in 'newOperator'.
     * @param factory   The factory used to create new instructions.
     * @param newOperator   The list of conditions to modify.
     * @return  Returns a new condition that joins the negation of all conditions in 'newOperator'.
     */
    private static CtExpression<Boolean> getNewConditionOperator(Factory factory, List<CtExpression<Boolean>> newOperator) {
        logger.info("Producing a new condition operator.");
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

    /**
     * The method where the 'condition' is negated.
     * @param factory   The factory used to create new instructions.
     * @param condition The condition to be negated.
     * @return  Returns the negated version fo 'condition'.
     */
    private static CtUnaryOperator<Boolean> negateCondition(Factory factory, CtExpression<Boolean> condition) {
        logger.info("Negating the condition {}.", condition.toString());
        CtUnaryOperator<Boolean> unaryOperator = factory.createUnaryOperator();
        unaryOperator.setKind(UnaryOperatorKind.NOT);
        unaryOperator.setOperand(condition);
        return unaryOperator;
    }

    /**
     * The method where the local variable creation is protected. If the type of the local variable is a primitive type
     * then the local variable will be replaced with an assignment inside the 'then' block of the newly created 'if'
     * statement.
     * @param factory   The factory used to create new instructions.
     * @param newBody   A block of statements that will become the new body of the vulnerable method.
     * @param localVariable The 'local variable' to be protected.
     * @param condition The condition to be used in the 'if' statement protecting the variable.
     * @return Returns the newly created 'if' statement.
     */
    private static CtIf protectLocalVariable(Factory factory, CtBlock<?> newBody,
                                             CtLocalVariable<?> localVariable, CtExpression<Boolean> condition) {
        logger.info("Protecting a local variable.");
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

        return newIf;
    }
}