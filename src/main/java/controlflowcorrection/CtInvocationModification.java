package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import util.NamingConvention;
import java.util.List;
import java.util.Optional;

public class CtInvocationModification {
    private static final Logger logger = LoggerFactory.getLogger(CtInvocationModification.class);

    static CtStatement modifyInvocation(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found an invocation to modify.");
        CtInvocation<?> invocation = (CtInvocation<?>) element;
        CtInvocation<?> newInvocation = invocation.clone();
        List<CtExpression<?>> invocationArguments = invocation.getArguments();
        CtExpression<?> target = invocation.getTarget();

        Optional<CtVariable<?>> optionalVariable = secretVariables.stream().filter(secret -> secret.getSimpleName().equals(target.toString())).findFirst();
        if (optionalVariable.isPresent()) {
            logger.info("Target is a secret.");
            CtVariable<?> variable = optionalVariable.get();
            CtExpression<?> defaultExpression = variable.getDefaultExpression();
            CtLocalVariable<?> newVariable = NamingConvention.produceNewVariable(factory, variable.getType(), defaultExpression);
            String variableName = newVariable.getSimpleName();

            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(target.toString(), variableName);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(variableName, newVariable.getType());

            CtCodeSnippetExpression<Object> newTarget = factory.createCodeSnippetExpression(variableName);
            newInvocation.setTarget(newTarget);
        }

        return newInvocation;
    }
}
