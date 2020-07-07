package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;

class CtArrayModification {
    private static final Logger logger = LoggerFactory.getLogger(CtArrayModification.class);

    static CtArrayRead<?> modifyArrayOperation(Factory factory, CtArrayRead<?> arrayRead) {
        logger.info("Modifying an array operation.");

        CtExpression<?> target = arrayRead.getTarget();
        CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();
        CtArrayRead<?> newArrayRead = arrayRead.clone();

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(target.toString())) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(target.toString());
            CtCodeSnippetExpression<?> newTarget = factory.createCodeSnippetExpression(valueVariablesReplacement);
            newArrayRead.setTarget(newTarget);
            logger.info("Changed the target of the array read.");
        }

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(indexExpression.toString())) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(indexExpression.toString());
            CtCodeSnippetExpression<Integer> newIndex = factory.createCodeSnippetExpression(valueVariablesReplacement);
            newArrayRead.setIndexExpression(newIndex);
            logger.info("Changed the index of the array read.");
        }

        return newArrayRead;
    }
}
