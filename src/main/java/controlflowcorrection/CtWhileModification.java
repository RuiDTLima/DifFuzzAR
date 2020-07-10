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

class CtWhileModification {
    private static final Logger logger = LoggerFactory.getLogger(CtWhileModification.class);

    static CtWhile[] modifyWhile(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a 'while' statement to modify.");
        CtWhile whileStatement = (CtWhile) element;
        CtWhile newWhileStatement = whileStatement.clone();

        if (initialStatement == null) {
            updateStoppingCondition(factory, whileStatement);
        }

        updateStoppingCondition(factory, newWhileStatement);
        CtBlock<?> whileBody = (CtBlock<?>) newWhileStatement.getBody();
        List<CtStatement> bodyStatements = whileBody.getStatements();
        CtStatementList[] bodyNewStatements = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, bodyStatements, initialStatement, dependableVariables, secretVariables);

        CtStatementList newBodyOldWhile = bodyNewStatements[0];
        CtStatementList newBodyNewWhile = bodyNewStatements[1];

        CtBlockImpl<?> oldCtBlock = new CtBlockImpl<>();
        newBodyOldWhile.forEach(ctStatement -> oldCtBlock.addStatement(ctStatement.clone()));    // Needs clone to avoid error by modify node parent.
        whileStatement.setBody(oldCtBlock);

        CtBlockImpl<?> newCtBlock = new CtBlockImpl<>();
        newBodyNewWhile.forEach(ctStatement -> newCtBlock.addStatement(ctStatement.clone()));    // Needs clone to avoid error by modify node parent.
        newWhileStatement.setBody(newCtBlock);

        return new CtWhile[]{whileStatement, newWhileStatement};
    }

    private static void updateStoppingCondition(Factory factory, CtWhile newWhileStatement) {
        CtBinaryOperator<Boolean> loopingExpression = (CtBinaryOperator<Boolean>) newWhileStatement.getLoopingExpression();
        CtExpression<?> leftHandOperand = loopingExpression.getLeftHandOperand();
        CtExpression<?> newLeftHandOperand = handleHandOperand(factory, leftHandOperand);
        loopingExpression.setLeftHandOperand(newLeftHandOperand);

        CtExpression<?> rightHandOperand = loopingExpression.getRightHandOperand();
        CtExpression<?> newRightHandOperand = handleHandOperand(factory, rightHandOperand);
        loopingExpression.setRightHandOperand(newRightHandOperand);
    }

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

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(handOperandString)) {
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