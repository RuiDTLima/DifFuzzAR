package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import util.NamingConvention;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class CtLocalVariableModification {
    private static final Logger logger = LoggerFactory.getLogger(CtLocalVariableModification.class);

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found a local variable while traversing the method.");

        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
        CtExpression<?> assignment = localVariable.getAssignment();

        if (assignment == null) {
            logger.info("There is no assignment to the local variable.");
            return;
        }

        boolean usesSecret = ControlFlowBasedVulnerabilityCorrection.usesSecret(assignment.toString(), secretVariables);
        logger.info("{} Does the assignment uses a secret? {}", assignment.toString(), usesSecret);

        if (usesSecret) {
            secretVariables.add(localVariable);
        }
    }

    static CtStatement modifyLocalVariable(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables) {
        logger.info("Found a local variable to modify");

        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
        CtStatement statement = null;
        CtExpression<?> assignment = localVariable.getAssignment();

        if (assignment instanceof CtInvocation) {
            logger.info("Assignment is an invocation.");

            CtInvocation<?> invocation = (CtInvocation<?>) assignment;
            List<CtExpression<?>> expressionList = new ArrayList<>(invocation.getArguments());
            expressionList.add(invocation.getTarget());

            boolean condition = expressionList.stream()
                    .anyMatch(ctExpression -> dependableVariables.stream()
                            .anyMatch(dependableVariable -> dependableVariable.equals(ctExpression.toString())));

            statement = modifyStatement(dependableVariables, localVariable, condition);
        } else {
            boolean condition = Arrays.stream(assignment.toString().split("\\."))
                    .anyMatch(word -> dependableVariables.stream().anyMatch(secretVariable -> secretVariable.equals(word)));

            statement = modifyStatement(dependableVariables, localVariable, condition);
        }
        return statement;
    }

    private static CtStatement modifyStatement(List<String> dependableVariables, CtLocalVariable<?> localVariable, boolean condition) {
        if (condition) {
            dependableVariables.add(localVariable.getSimpleName());
            logger.info("A new variable was added to the dependable variables.");
            return null;
        } else {
            return  (CtStatement) createNewLocalVariable(localVariable.clone());
        }
    }

    private static CtNamedElement createNewLocalVariable(CtLocalVariable<?> localVariable) {
        logger.info("A new local variable will be created.");
        //int counter = NamingConvention.increaseCounter();
        String newVariable = NamingConvention.produceNewVariableName();
        ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(localVariable.getSimpleName(), newVariable);
        return localVariable.setSimpleName(newVariable);
    }
}