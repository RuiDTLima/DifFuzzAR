package controlflowcorrection;

import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import java.util.List;

class CtWhileModification {
    static CtWhile modifyWhile(Factory factory, CtIfImpl initialStatement, CtWhile whileStatement, List<String> dependableVariables) {
        CtBinaryOperator<Boolean> loopingExpression = (CtBinaryOperator<Boolean>) whileStatement.getLoopingExpression();
        String leftHandOperand = loopingExpression.getLeftHandOperand().toString();
        String rightHandOperand = loopingExpression.getRightHandOperand().toString();

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(leftHandOperand)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(leftHandOperand);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(replacement);
            loopingExpression.setLeftHandOperand(expressionReplacement);
        }

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(rightHandOperand)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(rightHandOperand);
            CtCodeSnippetExpression<Object> expressionReplacement = factory.createCodeSnippetExpression(replacement);
            loopingExpression.setRightHandOperand(expressionReplacement);
        }

        CtBlock<?> whileBody = (CtBlock<?>) whileStatement.getBody();
        List<CtStatement> bodyStatements = whileBody.getStatements();
        CtStatementList bodyNewStatements = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, bodyStatements, initialStatement, dependableVariables);
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        bodyNewStatements.forEach(ctStatement -> ctBlock.addStatement(ctStatement.clone()));    // Needs clone to avoid error by modify node parent.
        whileStatement.setBody(ctBlock);
        return whileStatement;
    }
}
