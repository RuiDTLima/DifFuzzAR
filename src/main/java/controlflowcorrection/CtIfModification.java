package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import java.util.ArrayList;
import java.util.List;

class CtIfModification {
    private static final Logger logger = LoggerFactory.getLogger(CtIfModification.class);

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found an 'if' while traversing the method.");
        CtIfImpl ifStatement = (CtIfImpl) statement;

        if (ControlFlowBasedVulnerabilityCorrection.usesSecret(ifStatement.getCondition().toString(), secretVariables)) {
            logger.info("Found the source of vulnerability.");
            List<String> dependableVariables = new ArrayList<>();
            handleVulnerability(factory, ifStatement, dependableVariables);
        } else {
            logger.info("The current if condition does not depend on a secret. So the body of the 'then' and 'else' must be analysed.");
            CtBlock<?> thenBlock = ifStatement.getThenStatement();
            CtBlock<?> elseBlock = ifStatement.getElseStatement();
            ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, thenBlock, secretVariables, publicArguments);
            if (elseBlock != null)
                ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, elseBlock, secretVariables, publicArguments);
        }
    }

    private static CtBlock<?> handleVulnerability(Factory factory, CtIfImpl statement, List<String> dependableVariables) {
        logger.info("Handling the vulnerability");
        CtExpression<Boolean> condition = statement.getCondition();

        populateDependableVariables(dependableVariables, condition);

        CtBlock<?> returnBlock = new CtBlockImpl<>();

        CtBlock<?> thenStatement = statement.getThenStatement();
        CtBlock<?> elseStatement = statement.getElseStatement();

        List<CtStatement> thenStatements = thenStatement.clone().getStatements();
        CtStatementList thenStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatements, statement, dependableVariables);

        if (elseStatement == null) {
            logger.info("There is no else statement.");
            CtBlock<Object> block = factory.createBlock();
            if (thenStatement.getStatement(0) instanceof CtIf) {
                logger.info("The first statement in the 'then statement' is an 'if statement'");
                CtBlock<?> newBlock = modifyCondition(factory, statement, thenStatement.getStatement(0), dependableVariables);
                newBlock.getStatements().forEach(element -> block.addStatement(element.clone()));
                elseStatement = newBlock;
            } else {
                handleStatementList(returnBlock, thenStatementsList);
                elseStatement = block.insertEnd(thenStatementsList);
            }
            statement.setElseStatement(elseStatement);
        } else {
            logger.info("There is an else statement.");
            List<CtStatement> elseStatements = elseStatement.clone().getStatements();
            CtStatementList elseStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, elseStatements, statement, dependableVariables);

            handleStatementList(returnBlock, thenStatementsList);
            handleStatementList(returnBlock, elseStatementsList);

            thenStatement.insertBegin(elseStatementsList);
            elseStatement.insertBegin(thenStatementsList);
        }

        return returnBlock;
    }

    private static void populateDependableVariables(List<String> dependableVariables, CtExpression<?> condition) {
        logger.info("Populating the dependable variables.");
        if (condition instanceof CtBinaryOperator) {
            logger.info("Condition is a binary operator.");
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) condition;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

            handleHandOperand(dependableVariables, leftHandOperand);
            handleHandOperand(dependableVariables, rightHandOperand);

        } else if (condition instanceof CtInvocation) {
            logger.info("Condition is an invocation.");
            CtInvocation<?> invocation = (CtInvocation<?>) condition;
            CtExpression<?> target = invocation.getTarget();
            dependableVariables.add(target.toString());
        } else {
            dependableVariables.add(condition.toString());
        }
    }

    private static void handleStatementList(CtBlock<?> returnBlock, CtStatementList statementsList) {
        logger.info("Handling the statement list.");
        if (!statementsList.getStatements().isEmpty() && !(statementsList.getStatement(0) instanceof CtIf)) {
            statementsList.getStatements().forEach(element -> returnBlock.addStatement(element.clone()));
        }
    }

    private static CtBlock<?> modifyCondition(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables) {
        logger.info("Modifying the condition.");
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        CtExpression<Boolean> initialStatementCondition = initialStatement.getCondition();
        CtExpression<Boolean> condition = ifElement.getCondition();
        String initialStatementConditionTarget = findConditionTarget(initialStatementCondition);
        String conditionTarget = findConditionTarget(condition);

        if (condition instanceof CtInvocation) {
            logger.info("Condition is an invocation.");
            handleInvocationCondition(factory, initialStatement, ifElement, dependableVariables, ctBlock, (CtInvocation<Boolean>) condition, initialStatementConditionTarget, conditionTarget);
        }
        return ctBlock;
    }

    private static void handleHandOperand(List<String> dependableVariables, CtExpression<?> handOperand) {
        if (handOperand instanceof CtInvocation) {
            logger.info("Hand operand is an invocation.");
            CtInvocation<?> invocation = (CtInvocation<?>) handOperand;
            CtExpression<?> target = invocation.getTarget();
            dependableVariables.add(target.toString());
        } else if (handOperand instanceof CtBinaryOperator) {
            logger.info("Hand operand is a binary operator.");
            populateDependableVariables(dependableVariables, handOperand);
        }
    }

    private static String findConditionTarget(CtExpression<Boolean> initialStatementCondition) {
        logger.info("Finding the target of the condition.");
        CtExpression<?> possibleTarget = initialStatementCondition;
        while (possibleTarget instanceof CtInvocation) {
            possibleTarget = ((CtInvocation<?>) possibleTarget).getTarget();
        }
        return possibleTarget.toString();
    }

    private static void handleInvocationCondition(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables, CtBlockImpl<?> ctBlock, CtInvocation<Boolean> conditionInvocation, String initialStatementConditionTarget, String conditionTarget) {
        logger.info("Handling a condition that is an invocation.");
        List<CtExpression<?>> conditionInvocationArguments = conditionInvocation.getArguments();

        List<CtElement> invocations = new ArrayList<>();
        for (CtExpression<?> argument : conditionInvocationArguments) {
            if (argument instanceof CtInvocation &&
                    !((CtInvocation<?>) argument).getExecutable().getSimpleName().equals(initialStatementConditionTarget)) {

                invocations.add(argument);
            }
        }

        CtExecutableReference<?> executable = conditionInvocation.getExecutable();
        if (!executable.getSimpleName().equals(initialStatementConditionTarget)) {
            invocations.add(executable);
        }
        CtExpression<?> conditionInvocationTarget = conditionInvocation.getTarget();
        if (!initialStatementConditionTarget.equals(conditionTarget) && conditionInvocationTarget instanceof CtInvocation) {
            invocations.add(conditionInvocationTarget);
        }

        invocations.forEach(invocation -> {
            if (invocation instanceof CtInvocation) {
                logger.info("Invocation is of the type CtInvocation.");
                String type = ((CtInvocation<?>) invocation).getType().toString();
                String newVariable = createNewVariable(factory, ctBlock, invocation.toString(), type);
                CtCodeSnippetExpression<Object> expression = factory.createCodeSnippetExpression(newVariable);
                ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement("", expression.toString());
            } else if (invocation instanceof CtExecutableReference) {
                logger.info("Invocation is of the type CtExecutableReference.");
                handleExecutableReference(factory, initialStatement, ifElement, dependableVariables, ctBlock, (CtExecutableReference<?>) invocation);
            }
        });
    }

    private static void handleExecutableReference(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables, CtBlockImpl<?> ctBlock, CtExecutableReference<?> executableReference) {
        logger.info("Handling a executable reference.");
        List<CtTypeReference<?>> executableParameters = executableReference.getParameters();
        List<String> parametersVariable = new ArrayList<>(executableParameters.size());
        String oldVariable = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + ControlFlowBasedVulnerabilityCorrection.getCounter();
        String declaringType = executableReference.getDeclaringType().toString();

        for (int idx = 0; idx < executableParameters.size(); idx++) {
            String newVariable = createNewVariable(factory, ctBlock, oldVariable, declaringType);
            parametersVariable.add(newVariable);
        }

        String type = executableReference.getType().getSimpleName();
        if (type.equalsIgnoreCase("boolean")) {
            CtIf anIf = factory.createIf();
            CtCodeSnippetExpression<Boolean> codeSnippetStatement = factory.createCodeSnippetExpression(oldVariable + "." + executableReference.getSimpleName() + "(" + String.join(" ,", parametersVariable) + ")");
            anIf.setCondition(codeSnippetStatement);

            CtBlock<?> thenStatement = ifElement.getThenStatement();
            CtBlock<?> elseStatement = ifElement.getElseStatement();

            CtBlock<?> newThenBlock = createNewBlock(thenStatement, factory, initialStatement, dependableVariables);
            anIf.setThenStatement(newThenBlock);

            if (elseStatement != null) {
                CtBlock<?> newElseBlock = createNewBlock(elseStatement, factory, initialStatement, dependableVariables);
                anIf.setElseStatement(newElseBlock);
            }
            ctBlock.addStatement(anIf);
        }
    }

    private static String createNewVariable(Factory factory, CtBlockImpl<?> ctBlock, String oldVariable, String declaringType) {
        logger.info("Creating a new variable.");
        int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
        String newVariable = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter;
        CtCodeSnippetStatement variableInitiation = factory.createCodeSnippetStatement(declaringType + " " + newVariable + " = " + oldVariable);
        ctBlock.addStatement(variableInitiation);
        return newVariable;
    }

    private static CtBlock<?> createNewBlock( CtBlock<?> statement, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables) {
        logger.info("Creating a new block.");
        CtStatementList newElement = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, statement.clone().getStatements(), initialStatement, dependableVariables);
        CtBlock<?> newBlock = factory.createBlock();
        newElement.forEach(newThenStatement -> newBlock.addStatement(newThenStatement.clone()));
        return newBlock;
    }

    static CtBlock<?> modifyIf(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables) {
        logger.info("Modifying an 'if'.");
        CtIfImpl ifElement = (CtIfImpl) element;
        CtBlock<?> block = handleVulnerability(factory, ifElement, dependableVariables);

        if (!block.getStatements().isEmpty()) {
            initialStatement.setElseStatement(ifElement);
        }
        return block;
    }
}