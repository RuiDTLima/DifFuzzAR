package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import util.NamingConvention;

public class VariableReadModification {
    private static final Logger logger = LoggerFactory.getLogger(VariableReadModification.class);

    /**
     * The method where a variable read is modified. This is where a variable that is being replaced by a newly create
     * variable is replaced by that variable in a instructions that involves a variable read.
     * @param factory		The factory used to create new instructions.
     * @param variableRead	The variable read to be modified.
     * @return				Returns the new version of the variable read.
     */
    static CtExpression<?> modifyVariableRead(Factory factory, CtExpression<?> variableRead) {
        String handOperator = variableRead.toString();
        CtExpression<?> newHandOperator;

        if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(handOperator)) {
            String newHandOperatorVariable = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(handOperator);
            newHandOperator = factory.createCodeSnippetExpression(newHandOperatorVariable);
            logger.info("The hand operand is a variable that already was replaced.");
        } else {
            CtTypeReference<?> type = variableRead.getType();
            CtLocalVariable<?> localVariable = NamingConvention.produceNewVariable(factory, type, null);
            newHandOperator = factory.createVariableRead(localVariable.getReference(), false);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(handOperator, newHandOperator.toString());
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newHandOperator.toString(), type, null);
            logger.info("The hand operand is a variable that will now be replaced.");
        }
        return  newHandOperator;
    }
}