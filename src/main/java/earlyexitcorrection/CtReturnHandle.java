package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtReturnImpl;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class CtReturnHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtReturnHandle.class);

    static boolean handleReturn(Factory factory,
                                Iterator<CtCFlowBreak> returnsIterator,
                                CtLocalVariable<?> variable,
                                CtExpression<?> returnElement,
                                boolean afterCycleReturn,
                                CtBlock<?> newBody,
                                CtStatement currentStatement) {

        logger.info("Found a return.");
        returnsIterator.next();
        CtReturnImpl<?> returnStatement = (CtReturnImpl<?>) currentStatement;
        boolean isLastReturn = !returnsIterator.hasNext();
        CtStatement newStatement = handleReturnStatement(factory, returnStatement, variable, returnElement, isLastReturn);
        if (afterCycleReturn) {
            int numberOfStatement = newBody.getStatements().size();
            newBody.addStatement(numberOfStatement - 1, newStatement);
        } else {
            newBody.addStatement(newStatement);
        }
        return false;
    }

    private static CtStatement handleReturnStatement(Factory factory, CtReturnImpl<?> returnStatement,
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
            if (EarlyExitVulnerabilityCorrection.isKeyInProtectedVariables(simpleName)) {
                conditionsList = EarlyExitVulnerabilityCorrection.getProtectionOfVariable(simpleName);
            } else {
                conditionsList = new ArrayList<>();
            }
            conditionsList.add(ifCondition);
            EarlyExitVulnerabilityCorrection.addVariableProtection(simpleName, conditionsList);
        }
    }

    /**
     * Modifies the final return of the vulnerable method. This needs to be different because it will not only eliminate
     * a return expression but modified. If the last return was an expression and it wasn't already assigned to the return
     * variable it is now, and a new return expression is created.
     * @param factory   The factory used to create code snippets to add.
     * @param returnElement The element to return.
     * @param variable  The name of the variable to be returned.
     * @param returnImpl    The return instruction to be replaced.
     * @return
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

    private static CtAssignment<?, ?> alterReturnExpression(Factory factory, CtLocalVariable variable, CtReturnImpl<?> returnImpl) {
        CtExpression<?> returnedValue = returnImpl.getReturnedExpression();
        CtAssignment<?, ?> variableAssignment = factory.createVariableAssignment(variable.getReference(), false, returnedValue);
        variableAssignment.setType(variable.getType());
        return variableAssignment;
    }
}