package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import util.NamingConvention;
import java.util.List;
import java.util.Set;

class CtWhileModification {
    private static final Logger logger = LoggerFactory.getLogger(CtWhileModification.class);

    /**
     * The method where a 'while' statement is modified. Each statement in the body of the 'while' will be modified and a
     * slightly modified version of the body will become the new body while a completely modified version of the body will
     * become a new 'while' statement where the stopping condition is a modified version of the original 'while'.
     * @param element				The 'while' statement to be modified.
     * @param factory				The factory used to create new instructions.
     * @param initialStatement		The initial 'if' statement that started this modification.
     * @param dependableVariables	A set containing the dependable variables.
     * @param secretVariables		A list of secret variables.
     * @return						Returns an array of 'while' statements where in the first index is the original
     * 								'while' statement with slight modifications in the body, while in the second index
     * 								is a completely new version of the 'while' statement with a new body and a modified
     * 								stopping condition.
     */
    static CtWhile[] modifyWhile(CtElement element,
                                 Factory factory,
                                 CtIfImpl initialStatement,
                                 Set<String> dependableVariables,
                                 List<CtVariable<?>> secretVariables) {
        logger.info("Found a 'while' statement to modify.");
        CtWhile whileStatement = (CtWhile) element;
        CtWhile newWhileStatement = whileStatement.clone();

        updateStoppingCondition(factory, newWhileStatement);
        CtBlock<?> whileBody = (CtBlock<?>) newWhileStatement.getBody();
        List<CtStatement> bodyStatements = whileBody.getStatements();
        CtStatementList[] bodyNewStatements = ControlFlowBasedVulnerabilityCorrection
                .modifyStatements(factory, bodyStatements, initialStatement, dependableVariables, secretVariables);

        CtStatementList newBodyOldWhile = bodyNewStatements[0];
        CtStatementList newBodyNewWhile = bodyNewStatements[1];

        CtBlockImpl<?> oldCtBlock = new CtBlockImpl<>();
        // Needs clone to avoid error by modify node parent.
        newBodyOldWhile.forEach(ctStatement -> oldCtBlock.addStatement(ctStatement.clone()));
        whileStatement.setBody(oldCtBlock);

        CtBlockImpl<?> newCtBlock = new CtBlockImpl<>();
        // Needs clone to avoid error by modify node parent.
        newBodyNewWhile.forEach(ctStatement -> newCtBlock.addStatement(ctStatement.clone()));
        newWhileStatement.setBody(newCtBlock);

        return new CtWhile[]{whileStatement, newWhileStatement};
    }

    /**
     * The method where the stopping condition of the while statement is modified so that it does not use any variable
     * being replaced by a new one.
     * @param factory    		The factory used to create new instructions.
     * @param whileStatement    The 'while' statement to have its condition altered.
     */
    private static void updateStoppingCondition(Factory factory, CtWhile whileStatement) {
        CtExpression<Boolean> loopingExpression = whileStatement.getLoopingExpression();
        if (loopingExpression instanceof CtBinaryOperator) {
            CtBinaryOperator<Boolean> stoppingCondition = (CtBinaryOperator<Boolean>) whileStatement.getLoopingExpression();
            CtExpression<?> leftHandOperand = stoppingCondition.getLeftHandOperand();
            CtExpression<?> newLeftHandOperand = handleHandOperand(factory, leftHandOperand);
            stoppingCondition.setLeftHandOperand(newLeftHandOperand);

            CtExpression<?> rightHandOperand = stoppingCondition.getRightHandOperand();
            CtExpression<?> newRightHandOperand = handleHandOperand(factory, rightHandOperand);
            stoppingCondition.setRightHandOperand(newRightHandOperand);
        }
    }

    /**
     * The method where an hand operator of the binary operator is modified to try and remove a possible source of
     * vulnerability.
     * @param factory   	The factory used to create new instructions.
     * @param handOperand	The hand operator to be modified.
     * @return				Returns the newly modified version to the hand operator received.
     */
    private static CtExpression<?> handleHandOperand(Factory factory, CtExpression<?> handOperand) {
        String handOperandString;
        CtExpression newHandOperator;
        CtExpression<?> variable = handOperand;
        if (handOperand instanceof CtUnaryOperator) {
            CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) handOperand;
            variable = unaryOperator.getOperand();
            handOperandString = variable.toString();
        } else {
            handOperandString = handOperand.toString();
        }

        if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(handOperandString)) {
            logger.info("The left hand operand will be modified.");
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(handOperandString);
            newHandOperator = factory.createCodeSnippetExpression(replacement);
        } else if (variable instanceof CtVariableRead || variable instanceof CtVariableWrite) {
            String newVariableName = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newVariableName, handOperand.getType(), variable);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(handOperandString, newVariableName);
            newHandOperator = factory.createCodeSnippetExpression(newVariableName);
        } else {
            newHandOperator = variable;
        }

        if (handOperand instanceof CtUnaryOperator) {
            CtUnaryOperator unaryOperator = (CtUnaryOperator<?>) handOperand;
            CtUnaryOperator<?> newUnaryOperator = factory.createUnaryOperator();
            newUnaryOperator.setKind(unaryOperator.getKind());
            newUnaryOperator.setType(unaryOperator.getType());
            newUnaryOperator.setOperand(newHandOperator);
            return newUnaryOperator;
        }
        return newHandOperator;
    }
}