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

    static CtWhile modifyWhile(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a 'while' statement to modify.");
        CtWhile whileStatement = (CtWhile) element;

        if (initialStatement == null) {
            CtBinaryOperator<Boolean> loopingExpression = (CtBinaryOperator<Boolean>) whileStatement.getLoopingExpression();
            CtExpression<?> leftHandOperand = loopingExpression.getLeftHandOperand();
            CtExpression<?> newLeftHandOperand = handleHandOperand(factory, leftHandOperand);
            loopingExpression.setLeftHandOperand(newLeftHandOperand);

            CtExpression<?> rightHandOperand = loopingExpression.getRightHandOperand();
            CtExpression<?> newRightHandOperand = handleHandOperand(factory, rightHandOperand);
            loopingExpression.setRightHandOperand(newRightHandOperand);
        }

        CtBlock<?> whileBody = (CtBlock<?>) whileStatement.getBody();
        List<CtStatement> bodyStatements = whileBody.getStatements();
        CtStatementList bodyNewStatements = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, bodyStatements, initialStatement, dependableVariables, secretVariables);
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        bodyNewStatements.forEach(ctStatement -> ctBlock.addStatement(ctStatement.clone()));    // Needs clone to avoid error by modify node parent.
        whileStatement.setBody(ctBlock);
        element.replace(whileStatement);
        return whileStatement;
    }

    private static CtExpression<?> handleHandOperand(Factory factory, CtExpression<?> handOperand) {
        String handOperandString;
        if (handOperand instanceof CtUnaryOperator) {
            CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) handOperand;
            handOperandString = unaryOperator.getOperand().toString();
        } else {
            handOperandString = handOperand.toString();
        }

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(handOperandString)) {
            logger.info("The left hand operand will be modified.");
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(handOperandString);
            return factory.createCodeSnippetExpression(replacement);
        } else if (handOperand instanceof CtVariableRead) {
            String newVariable = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newVariable, handOperand.getType(), handOperand);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(handOperandString, newVariable);
            return factory.createCodeSnippetExpression(newVariable);
        }
        return handOperand;
    }
}