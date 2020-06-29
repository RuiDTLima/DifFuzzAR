package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtLiteralImpl;
import util.NamingConvention;

import java.util.List;

class CtWhileModification {
    private static final Logger logger = LoggerFactory.getLogger(CtWhileModification.class);

    static CtWhile modifyWhile(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a 'while' statement to modify.");

        CtWhile whileStatement = (CtWhile) element;
        CtBinaryOperator<Boolean> loopingExpression = (CtBinaryOperator<Boolean>) whileStatement.getLoopingExpression();
        CtExpression<?> leftHandOperand = loopingExpression.getLeftHandOperand();
        CtExpression<?> rightHandOperand = loopingExpression.getRightHandOperand();
        String leftHandOperandString = leftHandOperand.toString();
        String rightHandOperandString = rightHandOperand.toString();

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(leftHandOperandString)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(leftHandOperandString);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(replacement);
            loopingExpression.setLeftHandOperand(expressionReplacement);
            logger.info("The left hand operand was modified.");
        } else if (leftHandOperand instanceof CtVariableRead) {
            String newVariable = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newVariable, leftHandOperand.getType(), leftHandOperand);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(leftHandOperandString, newVariable);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(newVariable);
            loopingExpression.setLeftHandOperand(expressionReplacement);
        }

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(rightHandOperandString)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(rightHandOperandString);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(replacement);
            loopingExpression.setRightHandOperand(expressionReplacement);
            logger.info("The right hand operand was modified.");
        } else if (rightHandOperand instanceof CtVariableRead) {
            String newVariable = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newVariable, rightHandOperand.getType(), rightHandOperand);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(rightHandOperandString, newVariable);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(newVariable);
            loopingExpression.setRightHandOperand(expressionReplacement);
        }

        CtBlock<?> whileBody = (CtBlock<?>) whileStatement.getBody();
        List<CtStatement> bodyStatements = whileBody.getStatements();
        CtStatementList bodyNewStatements = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, bodyStatements, initialStatement, dependableVariables, secretVariables);
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        bodyNewStatements.forEach(ctStatement -> ctBlock.addStatement(ctStatement.clone()));    // Needs clone to avoid error by modify node parent.
        whileStatement.setBody(ctBlock);
        return whileStatement;
    }
}