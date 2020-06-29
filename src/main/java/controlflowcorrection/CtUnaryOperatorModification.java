package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import java.util.List;

class CtUnaryOperatorModification {
    private static final Logger logger = LoggerFactory.getLogger(CtUnaryOperatorModification.class);

    static CtStatement modifyUnaryOperator(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a unary operator to modify.");
        CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) element;
        CtStatement statement = unaryOperator;
        String operand = unaryOperator.getOperand().toString();

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(operand)) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(operand);
            CtExpression variableExpression = factory.createCodeSnippetExpression(valueVariablesReplacement);
            statement = unaryOperator.setOperand(variableExpression);
            logger.info("The unary operator was modified.");
        }
        return statement;
    }
}