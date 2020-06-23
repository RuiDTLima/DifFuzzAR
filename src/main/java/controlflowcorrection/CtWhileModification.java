package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import java.util.List;

class CtWhileModification {
    private static final Logger logger = LoggerFactory.getLogger(CtWhileModification.class);

    static CtWhile modifyWhile(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a 'while' statement to modify.");

        CtWhile whileStatement = (CtWhile) element;
        CtBinaryOperator<Boolean> loopingExpression = (CtBinaryOperator<Boolean>) whileStatement.getLoopingExpression();
        String leftHandOperand = loopingExpression.getLeftHandOperand().toString();
        String rightHandOperand = loopingExpression.getRightHandOperand().toString();

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(leftHandOperand)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(leftHandOperand);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(replacement);
            loopingExpression.setLeftHandOperand(expressionReplacement);
            logger.info("The left hand operand was modified.");
        }

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(rightHandOperand)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(rightHandOperand);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(replacement);
            loopingExpression.setRightHandOperand(expressionReplacement);
            logger.info("The right hand operand was modified.");
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
