package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
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

    static CtBlock<?>[] modifyUnaryOperator(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a unary operator to modify.");
        CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) element;
        CtStatement statement = unaryOperator.clone();
        String operand = unaryOperator.getOperand().toString();

        CtBlock<?> oldBlock = factory.createBlock().addStatement(unaryOperator.clone());

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(operand)) {
            String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(operand);
            CtExpression variableExpression = factory.createCodeSnippetExpression(valueVariablesReplacement);
            statement = unaryOperator.setOperand(variableExpression);
            logger.info("The unary operator was modified.");
        }
        CtBlock<?> newBlock = factory.createBlock().addStatement(statement.clone());

        return new CtBlock[]{oldBlock, newBlock};
    }
}