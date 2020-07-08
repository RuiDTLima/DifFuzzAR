package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.*;
import util.NamingConvention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class CtLocalVariableModification {
    private static final Logger logger = LoggerFactory.getLogger(CtLocalVariableModification.class);

    static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found a local variable while traversing the method.");

        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
        CtExpression<?> assignment = localVariable.getAssignment();

        CtBlock<?>[] returnBlocks = new CtBlock[2];
        returnBlocks[0] = new CtBlockImpl<>();
        returnBlocks[1] = new CtBlockImpl<>();
        returnBlocks[0].addStatement(localVariable.clone());
        returnBlocks[1].addStatement(null);

        if (assignment == null) {
            logger.info("There is no assignment to the local variable.");
            return returnBlocks;
        }

        boolean usesSecret = ControlFlowBasedVulnerabilityCorrection.usesSecret(assignment.toString(), secretVariables);
        logger.info("{} Does the assignment uses a secret? {}", assignment.toString(), usesSecret);

        if (usesSecret) {
            secretVariables.add(localVariable);
        }
        return returnBlocks;
    }

    static CtStatement[] modifyLocalVariable(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a local variable to modify");

        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
        Set<ModifierKind> modifiers = localVariable.getModifiers();
        CtExpression<?> assignment = localVariable.getAssignment();
        boolean condition;

        if (assignment instanceof CtInvocation) {
            condition = handleInvocation(dependableVariables, (CtInvocation<?>) assignment);
        } else if (assignment instanceof CtVariableReadImpl) {
            condition = handleVariableReadAssignment(dependableVariables, (CtVariableReadImpl<?>) assignment);
        } else if (assignment instanceof CtBinaryOperator) {
            condition = handleBinaryAssignment(dependableVariables, (CtBinaryOperator<?>) assignment);
        } else {
           condition = Arrays.stream(assignment.toString().split("\\."))
                    .anyMatch(word -> dependableVariables.stream().anyMatch(secretVariable -> secretVariable.equals(word)));
        }
        CtLocalVariable<?> newLocalVariable = modifyStatement(factory, dependableVariables, localVariable, condition, modifiers);
        return new CtStatement[]{localVariable, newLocalVariable};
    }

    private static boolean handleInvocation(List<String> dependableVariables, CtInvocation<?> invocation) {
        logger.info("Assignment is an invocation.");
        List<CtExpression<?>> expressionList = new ArrayList<>();

        List<CtExpression<?>> invocationArguments = invocation.getArguments();
        for (CtExpression<?> invocationArgument : invocationArguments) {
            if (invocationArgument instanceof CtArrayRead) {
                CtArrayRead<?> arrayRead = (CtArrayRead<?>) invocationArgument;

                CtExpression<?> target = arrayRead.getTarget();
                CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();

                expressionList.add(target);
                expressionList.add(indexExpression);
            } else if (invocationArgument instanceof CtVariableRead) {
                CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationArgument;
                expressionList.add(variableRead);
            } else if (invocationArgument instanceof CtInvocationImpl) {
                CtInvocationImpl<?> invocationImpl = (CtInvocationImpl<?>) invocationArgument;
                CtExpression<?> target = invocationImpl.getTarget();
                List<CtExpression<?>> arguments = invocationImpl.getArguments();

                expressionList.add(target);
                List<CtExpression<?>> collect = arguments.stream().filter(argument -> !(argument instanceof CtLiteralImpl)).collect(Collectors.toList());
                expressionList.addAll(collect);
            }
        }

        expressionList.add(invocation.getTarget());

        return expressionList.stream()
                .anyMatch(ctExpression -> dependableVariables.stream()
                        .anyMatch(dependableVariable -> dependableVariable.equals(ctExpression.toString())));
    }

    private static boolean handleVariableReadAssignment(List<String> dependableVariables, CtVariableReadImpl<?> assignment) {
        CtVariableReadImpl<?> variableRead = assignment;
        String variable = variableRead.getVariable().toString();
        return dependableVariables.stream().anyMatch(dependableVariable -> dependableVariable.equals(variable));
    }

    private static boolean handleBinaryAssignment(List<String> dependableVariables, CtBinaryOperator<?> binaryOperator) {
        boolean condition = false;
        CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
        CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

        if (leftHandOperand instanceof CtBinaryOperator) {
            condition = handleBinaryAssignment(dependableVariables, (CtBinaryOperator<?>) leftHandOperand);
        } else if (leftHandOperand instanceof CtVariableReadImpl) {
            condition = handleVariableReadAssignment(dependableVariables, (CtVariableReadImpl<?>) leftHandOperand);
        }
        if (rightHandOperand instanceof CtBinaryOperator) {
            condition = handleBinaryAssignment(dependableVariables, (CtBinaryOperator<?>) rightHandOperand);
        } else if (rightHandOperand instanceof CtVariableReadImpl) {
            condition = handleVariableReadAssignment(dependableVariables, (CtVariableReadImpl<?>) rightHandOperand);
        }
        return condition;
    }

    private static CtLocalVariable<?> modifyStatement(Factory factory, List<String> dependableVariables, CtLocalVariable<?> localVariable, boolean condition, Set<ModifierKind> modifiers) {
        if (condition) {
            dependableVariables.add(localVariable.getSimpleName());
            logger.info("A new variable was added to the dependable variables.");
            return null;
        } else {
            CtLocalVariable<?> newLocalVariable = createNewLocalVariable(factory, localVariable);
            newLocalVariable.setModifiers(modifiers);
            return newLocalVariable;
        }
    }

    private static CtLocalVariable<?> createNewLocalVariable(Factory factory, CtLocalVariable localVariable) {
        logger.info("A new local variable will be created.");
        String newVariable = NamingConvention.produceNewVariable();
        ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(localVariable.getSimpleName(), newVariable);

        return factory.createLocalVariable(localVariable.getType(), newVariable, localVariable.getDefaultExpression());
    }
}