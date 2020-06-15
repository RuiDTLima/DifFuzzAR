package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class CtLocalVariableModification {
    private static final Logger logger = LoggerFactory.getLogger(CtLocalVariableModification.class);

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
        CtExpression<?> assignment = localVariable.getAssignment();
        if (assignment == null)
            return;

        boolean usesSecret = ControlFlowBasedVulnerabilityCorrection.usesSecret(assignment.toString(), secretVariables);
        logger.info("{} uses a secret? {}", assignment.toString(), usesSecret);
        if (usesSecret) {
            secretVariables.add(localVariable);
        }
    }

    static CtStatement modifyLocalVariable(List<String> dependableVariables, CtLocalVariable<?> localVariable) {
        CtStatement statement = null;
        CtExpression<?> assignment = localVariable.getAssignment();
        if (assignment instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) assignment;
            List<CtExpression<?>> expressionList = new ArrayList<>(invocation.getArguments());
            expressionList.add(invocation.getTarget());
            if (expressionList.stream().anyMatch(ctExpression -> dependableVariables.stream().anyMatch(dependableVariable -> dependableVariable.equals(ctExpression.toString())))) {
                dependableVariables.add(localVariable.getSimpleName());
            } else {
                statement = (CtStatement) createNewLocalVariable(localVariable.clone());
            }
        } else if (Arrays.stream(assignment.toString().split("\\.")).anyMatch(word -> dependableVariables.stream().anyMatch(secretVariable -> secretVariable.equals(word)))) {
            dependableVariables.add(localVariable.getSimpleName());
        } else {
            statement = (CtStatement) createNewLocalVariable(localVariable.clone());
        }
        return statement;
    }

    private static CtNamedElement createNewLocalVariable(CtLocalVariable<?> localVariable) {
        int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
        String newVariable = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter;
        ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(localVariable.getSimpleName(), newVariable);
        return localVariable.setSimpleName(newVariable);
    }
}