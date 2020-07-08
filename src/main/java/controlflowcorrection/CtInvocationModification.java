package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.*;
import util.NamingConvention;
import java.util.List;
import java.util.Optional;

public class CtInvocationModification {
    private static final Logger logger = LoggerFactory.getLogger(CtInvocationModification.class);

    static CtBlock<?>[] modifyInvocation(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found an invocation to modify.");
        CtInvocation<?> invocation = (CtInvocation<?>) element;
        CtInvocation<?> newInvocation = invocation.clone();
        List<CtExpression<?>> invocationArguments = invocation.getArguments();
        CtExpression<?> target = invocation.getTarget();
        CtBlock<?> oldBlock = factory.createBlock();
        CtBlock<?> newBlock = factory.createBlock();

        oldBlock.addStatement(invocation.clone());

        if (usesDependable(invocation, dependableVariables)) {
            return new CtBlock[]{oldBlock, newBlock};
        }

        Optional<CtVariable<?>> optionalVariable = secretVariables.stream().filter(secret -> secret.getSimpleName().equals(target.toString())).findFirst();
        if (optionalVariable.isPresent()) {
            logger.info("Target is a secret.");
            increaseDependableVariable(initialStatement, dependableVariables);
            CtVariable<?> variable = optionalVariable.get();
            CtExpression<?> defaultExpression = variable.getDefaultExpression();
            CtExpression<?> newDefaultExpression = null;

            if (defaultExpression instanceof CtConstructorCallImpl) {
                CtConstructorCallImpl constructorCall = (CtConstructorCallImpl<?>) defaultExpression;
                List<CtExpression<?>> arguments = constructorCall.getArguments();

                CtConstructorCall<?> newConstructorCall = factory.createConstructorCall();
                newConstructorCall.setType(constructorCall.getType());
                newConstructorCall.setTarget(constructorCall.getTarget());

                for (CtExpression<?> argument : arguments) {
                    Optional<CtVariable<?>> first = secretVariables.stream().filter(secret -> secret.getSimpleName().equals(argument.toString())).findFirst();
                    CtVariable<?> secretVariable = first.get();
                    CtExpression<?> secretDefaultExpression = secretVariable.getDefaultExpression();
                    if (secretDefaultExpression instanceof CtBinaryOperatorImpl) {
                        CtBinaryOperatorImpl<?> binaryOperator = (CtBinaryOperatorImpl<?>) secretDefaultExpression;
                        CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
                        CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

                        if (!dependableVariables.contains(leftHandOperand.toString())) {
                            newConstructorCall.addArgument(leftHandOperand);
                        } else if (!dependableVariables.contains(rightHandOperand.toString())) {
                            newConstructorCall.addArgument(rightHandOperand);
                        }
                    }
                }
                newDefaultExpression = newConstructorCall;
            }

            CtLocalVariable<?> newVariable = NamingConvention.produceNewVariable(factory, variable.getType(), newDefaultExpression);
            String variableName = newVariable.getSimpleName();

            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(target.toString(), variableName);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(variableName, newVariable.getType(), newDefaultExpression);

            CtCodeSnippetExpression<Object> newTarget = factory.createCodeSnippetExpression(variableName);
            newInvocation.setTarget(newTarget);
        }

        newBlock.addStatement(newInvocation.clone());

        return new CtBlock[]{oldBlock, newBlock};
    }

    private static void increaseDependableVariable(CtIfImpl initialStatement, List<String> dependableVariables) {
        CtElement blockParent = initialStatement.getParent();
        if (blockParent.isParentInitialized()) {
            CtElement parent = blockParent.getParent();
            if (parent instanceof CtForImpl) {
                CtForImpl forImpl = (CtForImpl) parent;
                CtExpression<Boolean> stoppingCondition = forImpl.getExpression();
                if (stoppingCondition instanceof CtBinaryOperatorImpl) {
                    CtBinaryOperatorImpl<?> stoppingOperator = (CtBinaryOperatorImpl<?>) stoppingCondition;
                    CtExpression<?> leftHandOperand = stoppingOperator.getLeftHandOperand();
                    CtExpression<?> rightHandOperand = stoppingOperator.getRightHandOperand();
                    dependableVariables.add(leftHandOperand.toString());
                    dependableVariables.add(rightHandOperand.toString());
                }
            }
        }
    }

    private static boolean usesDependable(CtExpression<?> defaultExpression, List<String> dependableVariables) {
        boolean usesDependable = false;
        if (defaultExpression instanceof CtInvocationImpl) {
            CtInvocationImpl<?> constructorCall = (CtInvocationImpl<?>) defaultExpression;
            List<CtExpression<?>> arguments = constructorCall.getArguments();

            for (CtExpression<?> argument : arguments) {
                if (dependableVariables.contains(argument.toString())) {
                    usesDependable = true;
                }
            }
        }
        return usesDependable;
    }
}
