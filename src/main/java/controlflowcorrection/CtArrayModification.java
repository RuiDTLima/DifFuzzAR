package controlflowcorrection;

import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;

class CtArrayModification {
    static CtArrayRead<?> modifyArrayOperation(Factory factory, CtArrayRead<?> arrayRead) {
        CtExpression<?> target = arrayRead.getTarget();
        CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();
        CtArrayRead<?> newArrayRead = factory.createArrayRead();
        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(target.toString())) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(target.toString());
            CtCodeSnippetExpression<?> newTarget = factory.createCodeSnippetExpression(valueVariablesReplacement);
            newArrayRead.setTarget(newTarget);
        } else {
            newArrayRead.setTarget(target);
        }
        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(indexExpression.toString())) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(indexExpression.toString());
            CtCodeSnippetExpression<Integer> newIndex = factory.createCodeSnippetExpression(valueVariablesReplacement);
            newArrayRead.setIndexExpression(newIndex);
        }
        return newArrayRead;
    }
}
