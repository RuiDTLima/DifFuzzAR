package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.*;
import util.NamingConvention;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CtInvocationModification {
    private static final Logger logger = LoggerFactory.getLogger(CtInvocationModification.class);

    /**
     * The method where an invocation is modified. If any of the invocation arguments uses a dependable variable then no
     * modification is performed. If the target of the invocation is a secret variable, then a new variable will be created
     * to be used as the invocation target.
     * @param element	            The invocation element to be modified.
     * @param factory	            The factory used to create new instructions.
     * @param initialStatement	    The initial 'if' statement that originated the modification.
     * @param dependableVariables	A set of dependable variables.
     * @param secretVariables	    A list of secret variables
     * @return	                    Returns an array of blocks where in the first index is a block with the original
     *                              invocation and in the second index is a block with the modified invocation.
     */
    static CtBlock<?>[] modifyInvocation(CtElement element,
                                         Factory factory,
                                         CtIfImpl initialStatement,
                                         Set<String> dependableVariables,
                                         List<CtVariable<?>> secretVariables) {
        logger.info("Found an invocation to modify.");
        CtInvocation<?> invocation = (CtInvocation<?>) element;
        CtInvocation<?> newInvocation = invocation.clone();
        List<CtExpression<?>> invocationArguments = invocation.getArguments();
        CtExpression<?> target = invocation.getTarget();
        CtBlock<?> oldBlock = factory.createBlock();
        CtBlock<?> newBlock = factory.createBlock();

        oldBlock.addStatement(invocation.clone());

        if (usesDependable(invocationArguments, dependableVariables)) {
            return new CtBlock[]{oldBlock, newBlock};
        }

        secretVariables
                .stream()
                .filter(secret -> secret.getSimpleName().equals(target.toString()))
                .findFirst()
                .ifPresent(variable -> {
                    logger.info("Target is a secret.");
                    increaseDependableVariable(initialStatement, dependableVariables);
                    CtExpression<?> defaultExpression = variable.getDefaultExpression();
                    CtExpression<?> newDefaultExpression = null;

                    if (defaultExpression instanceof CtConstructorCallImpl) {
                        CtConstructorCallImpl<?> constructorCall = (CtConstructorCallImpl<?>) defaultExpression;
                        newDefaultExpression = createNewDefaultExpression(factory, dependableVariables, secretVariables, constructorCall);
                    }

                    CtLocalVariable<?> newVariable = NamingConvention.produceNewVariable(factory, variable.getType(), newDefaultExpression);
                    String variableName = newVariable.getSimpleName();

                    ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(target.toString(), variableName);
                    ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(variableName, newVariable.getType(), newDefaultExpression);

                    CtCodeSnippetExpression<Object> newTarget = factory.createCodeSnippetExpression(variableName);
                    newInvocation.setTarget(newTarget);
                });

        newBlock.addStatement(newInvocation.clone());

        return new CtBlock[]{oldBlock, newBlock};
    }

    /**
     * A method to check if any of the arguments used in the invocation is a dependable variable.
     * @param arguments	            A list of the arguments used in the invocation.
     * @param dependableVariables	A set containing the dependable variables.
     * @return	                    True if any of the arguments is a dependable variable, false otherwise.
     */
    private static boolean usesDependable(List<CtExpression<?>> arguments, Set<String> dependableVariables) {
        return arguments
                .stream()
                .anyMatch(argument -> dependableVariables.contains(argument.toString()));
    }

    /**
     * A method where variables are added to the set of dependable variables. As it stands it only adds the variables if
     * the 'if' statement that started the modification is inside a 'for' cycle and the stopping condition is a binary
     * operator.
     * @param initialStatement      The initial 'if' statement that originated the modification.
     * @param dependableVariables	A set containing the dependable variables.
     */
    private static void increaseDependableVariable(CtIfImpl initialStatement, Set<String> dependableVariables) {
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

    /**
     * The method where a new constructor call is created to replace an existing one so that in the new constructor call
     * no dependable variable is used as an argument.
     * @param factory	            The factory used to create new instructions.
     * @param dependableVariables	A set containing the dependable variables.
     * @param secretVariables	    A list of the secret variables.
     * @param constructorCall	    The constructor call to be replaced.
     * @return	                    Returns a new constructor call that does not uses any dependable variable.
     */
    private static CtConstructorCall<?> createNewDefaultExpression(Factory factory, Set<String> dependableVariables,
                                                                   List<CtVariable<?>> secretVariables,
                                                                   CtConstructorCallImpl<?> constructorCall) {
        List<CtExpression<?>> arguments = constructorCall.getArguments();

        CtConstructorCall<?> newConstructorCall = factory.createConstructorCall();
        CtTypeReference type = constructorCall.getType();
        newConstructorCall.setType(type);
        newConstructorCall.setTarget(constructorCall.getTarget());

        for (CtExpression<?> argument : arguments) {
            Optional<CtVariable<?>> optionalVariable = secretVariables
                    .stream()
                    .filter(secret -> secret.getSimpleName().equals(argument.toString()))
                    .findFirst();

            if (optionalVariable.isPresent()) {
                CtVariable<?> secretVariable = optionalVariable.get();
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
            } else {
                newConstructorCall.addArgument(argument);
            }
        }
        return newConstructorCall;
    }
}