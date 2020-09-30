package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.util.Iterator;
import java.util.Set;

class CtLocalVariableHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtLocalVariableHandle.class);

    /**
     * The method where a 'local variable' is handled. An attempt will be made to protect the local variable. If the
     * value being assigned is a binary operator then a condition will be added considering each hand of the assignment.
     * @param factory           The factory used to create new instructions.
     * @param returnsIterator   An iterator over the returns of the method. NOT USED.
     * @param returnVariable    The variable to be returned. NOT USED.
     * @param returnElement     The valid return expression returned in the final return statement. NOT USED.
     * @param afterWhileReturn  Indicates if this local variable happens after a 'while' statement. NOT USED.
     * @param newBody           A block of statements that will become the new body of the vulnerable method.
     * @param currentStatement  The 'local variable' statement under analysis.
     * @return                  Indicates if the next statement will be after a 'while' cycle.
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
        if (assignment instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) assignment;
            String leftHandOperand = binaryOperator.getLeftHandOperand().toString();
            String rightHandOperand = binaryOperator.getRightHandOperand().toString();

            newStatement = addProtection(factory, newBody, localVariable, newStatement, leftHandOperand, rightHandOperand);
        } else if (assignment instanceof CtArrayRead) {
            CtArrayRead<?> arrayRead = (CtArrayRead<?>) assignment;
            String target = arrayRead.getTarget().toString();
            CtExpression<Integer> index = arrayRead.getIndexExpression();
            String indexVariable;

            if (index instanceof CtUnaryOperator) {
                CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) index;
                indexVariable = unaryOperator.getOperand().toString();
            } else {
                indexVariable = index.toString();
            }

            newStatement = addProtection(factory, newBody, localVariable, newStatement, target, indexVariable);
        }
        newBody.addStatement(newStatement);
        return false;
    }

    /**
     * Method where the necessary protection to the used of the variables 'firstVariable' and 'secondVariable' is created.
     * @param factory           The factory used to create new instructions.
     * @param newBody           A block of statements that will become the new body of the vulnerable method.
     * @param localVariable     The 'local variable' statement to protect.
     * @param newStatement      The new statement containing the protection.
     * @param firstVariable     The first variable to be protected.
     * @param secondVariable    The second variable to be protected.
     * @return  Returns a new statement that protects the original one.
     */
    private static CtStatement addProtection(Factory factory,
                                             CtBlock<?> newBody,
                                             CtLocalVariable<?> localVariable,
                                             CtStatement newStatement,
                                             String firstVariable,
                                             String secondVariable) {

        Set<CtExpression<Boolean>> newLeftOperator = EarlyExitVulnerabilityCorrection.getProtectionOfVariableOrEmpty(firstVariable);
        Set<CtExpression<Boolean>> newRightOperator = EarlyExitVulnerabilityCorrection.getProtectionOfVariableOrEmpty(secondVariable);

        newLeftOperator.removeAll(newRightOperator);

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
        return newStatement;
    }

    /**
     * The method where a new condition is produced considering the operators received in 'newOperator'.
     * @param factory       The factory used to create new instructions.
     * @param newOperator   The list of conditions to modify.
     * @return              Returns a new condition that joins the negation of all conditions in 'newOperator'.
     */
    private static CtExpression<Boolean> getNewConditionOperator(Factory factory, Set<CtExpression<Boolean>> newOperator) {
        logger.info("Producing a new condition operator.");
        CtExpression<Boolean> newCondition = null;

        Iterator<CtExpression<Boolean>> iterator = newOperator.iterator();

        while (iterator.hasNext()) {
            CtExpression<Boolean> current = iterator.next();
            if (newCondition == null && iterator.hasNext()) {
                CtExpression<Boolean> left = negateCondition(factory, current);
                CtExpression<Boolean> next = iterator.next();
                CtExpression<Boolean> right = negateCondition(factory, next);
                newCondition = factory.createBinaryOperator(left, right, BinaryOperatorKind.AND);
            } else if (newCondition == null) {
                newCondition = negateCondition(factory, current);
            } else {
                newCondition = factory.createBinaryOperator(newCondition, negateCondition(factory, current), BinaryOperatorKind.AND);
            }
        }
        return newCondition;
    }

    /**
     * The method where the 'condition' is negated.
     * @param factory   The factory used to create new instructions.
     * @param condition The condition to be negated.
     * @return          Returns the negated version fo 'condition'.
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
     * @param factory       The factory used to create new instructions.
     * @param newBody       A block of statements that will become the new body of the vulnerable method.
     * @param localVariable The 'local variable' to be protected.
     * @param condition     The condition to be used in the 'if' statement protecting the variable.
     * @return              Returns the newly created 'if' statement.
     */
    private static CtIf protectLocalVariable(Factory factory,
                                             CtBlock<?> newBody,
                                             CtLocalVariable<?> localVariable,
                                             CtExpression<Boolean> condition) {
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