package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtReturnImpl;
import java.util.Iterator;
import java.util.List;

class CtReturnHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtReturnHandle.class);

    /**
     * The method where a return is handled. Here the current return statement will be modified to become an assignment.
     * If it happens after a 'while' statement then the new assignment must be added before the 'while' and not after.
     * @param factory           The factory used to create new instructions.
     * @param returnsIterator   An iterator over the returns of the method.
     * @param returnVariable    The variable to be returned.
     * @param returnElement     The valid return expression returned in the final return statement.
     * @param afterWhileReturn  Indicates if this 'return' statement happens after a 'while' statement.
     * @param newBody           A block of statements that will become the new body of the vulnerable method.
     * @param currentStatement  The 'local variable' statement under analysis.
     * @return                  Indicates if the next instructions happens after a 'while' cycle.
     */
    static boolean handleReturn(Factory factory,
                                Iterator<CtCFlowBreak> returnsIterator,
                                CtLocalVariable<?> returnVariable,
                                CtExpression<?> returnElement,
                                boolean afterWhileReturn,
                                CtBlock<?> newBody,
                                CtStatement currentStatement) {

        logger.info("Found a return.");
        returnsIterator.next();
        CtReturnImpl<?> returnStatement = (CtReturnImpl<?>) currentStatement;
        boolean isLastReturn = !returnsIterator.hasNext();
        CtStatement newStatement = handleReturnStatement(factory, returnStatement, returnVariable, returnElement, isLastReturn);
        if (afterWhileReturn) {
            int numberOfStatement = newBody.getStatements().size();
            newBody.addStatement(numberOfStatement - 1, newStatement);
        } else {
            newBody.addStatement(newStatement);
        }
        return false;
    }

    /**
     * The method where the return statement is modified depending on whether it is part of an 'if' statement, if it is
     * the last return or none of the previous.
     * @param factory           The factory used to create new instructions.
     * @param returnStatement   The return statement being modified.
     * @param variable          The variable to be returned.
     * @param returnElement     The valid return expression returned in the final return statement.
     * @param isLastReturn      Indicates if this is the last return statement of the method.
     * @return                  Returns the modified return statement.
     */
    private static CtStatement handleReturnStatement(Factory factory,
                                                     CtReturnImpl<?> returnStatement,
                                                     CtLocalVariable<?> variable,
                                                     CtExpression<?> returnElement,
                                                     boolean isLastReturn) {

        CtElement parentElement = returnStatement.getParent().getParent();
        if (parentElement instanceof CtIfImpl) {
            CtIfImpl ifStatement = (CtIfImpl) parentElement;
            CtExpression<Boolean> condition = ifStatement.getCondition();
            saveCondition(condition, condition);
            CtIfHandle.modifyIfCondition(factory, ifStatement);
        } else if (isLastReturn) {
            return modifyLastReturn(factory, returnElement, variable, returnStatement);
        }
        return alterReturnExpression(factory, variable, returnStatement);
    }

    /**
     * Saves the condition in 'ifCondition' as condition protecting each variable in that same condition.
     * @param ifCondition       The condition to be saved as protecting variables.
     * @param currentCondition  The sub-condition under analysis in the moment.
     */
    private static void saveCondition(CtExpression<Boolean> ifCondition, CtExpression<?> currentCondition) {
        if (currentCondition instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) currentCondition;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();
            saveCondition(ifCondition, leftHandOperand);
            saveCondition(ifCondition, rightHandOperand);
        } else if (currentCondition instanceof CtVariableRead) {
            CtVariableRead<?> variableRead = (CtVariableRead<?>) currentCondition;
            String simpleName = variableRead.getVariable().getSimpleName();
            List<CtExpression<Boolean>> conditionsList;
            conditionsList = EarlyExitVulnerabilityCorrection.getProtectionOfVariableOrEmpty(simpleName);
            conditionsList.add(ifCondition);
            EarlyExitVulnerabilityCorrection.addVariableProtection(simpleName, conditionsList);
        }
    }

    /**
     * Modifies the final return of the vulnerable method. This needs to be different because it will not only eliminate
     * a return expression but modify it. If the last return was an expression and it wasn't already assigned to the return
     * variable it is now, and a new return expression is created.
     * @param factory       The factory used to create new instructions.
     * @param returnElement The element to return.
     * @param variable      The name of the variable to be returned.
     * @param returnImpl    The return instruction to be replaced.
     * @return              Returns the new statement replacing the return.
     */
    private static CtStatement modifyLastReturn(Factory factory,
                                                CtExpression<?> returnElement,
                                                CtLocalVariable<?> variable,
                                                CtReturnImpl<?> returnImpl) {

        CtExpression<?> returnedValue = returnImpl.getReturnedExpression();
        CtReturn<?> objectCtReturn = factory.createReturn().setReturnedExpression(factory.createCodeSnippetExpression(variable.getSimpleName()));

        if (returnElement == null || !returnElement.toString().contains(returnedValue.toString())) {
            return alterReturnExpression(factory, variable, returnImpl);
        } else {
            returnImpl.replace(objectCtReturn);
        }
        return null;
    }

    /**
     * Transform the return statement into an assignment of the to return variable.
     * @param factory       The factory used to create new instructions.
     * @param variable      The variable to be returned.
     * @param returnImpl    The return statement to transform.
     * @return              Returns the newly created assignment statement.
     */
    private static CtAssignment<?, ?> alterReturnExpression(Factory factory, CtLocalVariable variable, CtReturnImpl<?> returnImpl) {
        CtExpression<?> returnedValue = returnImpl.getReturnedExpression();
        CtAssignment<?, ?> variableAssignment = factory.createVariableAssignment(variable.getReference(), false, returnedValue);
        variableAssignment.setType(variable.getType());
        return variableAssignment;
    }
}