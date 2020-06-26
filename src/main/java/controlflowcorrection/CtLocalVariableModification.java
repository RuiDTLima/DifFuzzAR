package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtLiteralImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import util.NamingConvention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    static CtStatement modifyLocalVariable(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found a local variable to modify");

        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
        CtStatement statement;
        boolean condition;
        CtExpression<?> assignment = localVariable.getAssignment();

        if (assignment instanceof CtInvocation) {
            logger.info("Assignment is an invocation.");

            List<CtExpression<?>> expressionList = new ArrayList<>();

            CtInvocation<?> invocation = (CtInvocation<?>) assignment;
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

            condition = expressionList.stream()
                    .anyMatch(ctExpression -> dependableVariables.stream()
                            .anyMatch(dependableVariable -> dependableVariable.equals(ctExpression.toString())));

        } else if (assignment instanceof CtVariableReadImpl) {
            condition = handleVariableReadAssignment(dependableVariables, (CtVariableReadImpl<?>) assignment);
        } else if (assignment instanceof CtBinaryOperator) {
            condition = handleBinaryAssignment(dependableVariables, (CtBinaryOperator<?>) assignment);
        } else {
           condition = Arrays.stream(assignment.toString().split("\\."))
                    .anyMatch(word -> dependableVariables.stream().anyMatch(secretVariable -> secretVariable.equals(word)));
        }
        statement = modifyStatement(dependableVariables, localVariable, condition);
        return statement;
    }

    private static boolean handleVariableReadAssignment(List<String> dependableVariables, CtVariableReadImpl<?> assignment) {
        boolean condition;
        CtVariableReadImpl<?> variableRead = assignment;
        String variable = variableRead.getVariable().toString();
        condition = dependableVariables.stream().anyMatch(dependableVariable -> dependableVariable.equals(variable));
        return condition;
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
        String newVariable = NamingConvention.produceNewVariable();
        ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(localVariable.getSimpleName(), newVariable);
        return localVariable.setSimpleName(newVariable);
    }
}