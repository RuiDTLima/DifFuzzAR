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
import spoon.support.reflect.code.*;
import util.EqualStatementsFunction;
import util.NamingConvention;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

class CtIfModification {
    private static final Logger logger = LoggerFactory.getLogger(CtIfModification.class);
    private static final HashMap<Class<?>, EqualStatementsFunction<CtStatement, CtStatement>> equalsFunctions = new HashMap<>();

    static {
        populateFunction();
    }

    private static void populateFunction() {
        equalsFunctions.put(CtAssignmentImpl.class, CtAssignmentModification::equalAssignments);
    }

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found an 'if' while traversing the method.");
        CtIfImpl ifStatement = (CtIfImpl) statement;

        if (ControlFlowBasedVulnerabilityCorrection.usesSecret(ifStatement.getCondition().toString(), secretVariables)) {
            logger.info("Found the source of vulnerability.");
            List<String> dependableVariables = new ArrayList<>();
            handleVulnerability(factory, ifStatement, dependableVariables, secretVariables);
        } else {
            logger.info("The current if condition does not depend on a secret. So the body of the 'then' and 'else' must be analysed.");
            CtBlock<?> thenBlock = ifStatement.getThenStatement();
            CtBlock<?> elseBlock = ifStatement.getElseStatement();
            ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, thenBlock, secretVariables, publicArguments);
            if (elseBlock != null)
                ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, elseBlock, secretVariables, publicArguments);
        }
    }

    private static CtBlock<?> handleVulnerability(Factory factory, CtIfImpl statement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Handling the vulnerability");
        CtExpression<Boolean> condition = statement.getCondition();

        populateDependableVariables(dependableVariables, condition);

        CtBlock<?> returnBlock = new CtBlockImpl<>();

        CtBlock<?> thenStatement = statement.getThenStatement();
        CtBlock<?> elseStatement = statement.getElseStatement();

        List<CtStatement> thenStatements = thenStatement.clone().getStatements();
        CtStatementList thenStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatements, statement, dependableVariables, secretVariables);

        if (elseStatement == null) {
            logger.info("There is no else statement.");
            if (thenStatementsList.getStatements().size() != 0) {
                CtBlock<Object> block = factory.createBlock();
                if (thenStatement.getStatement(0) instanceof CtIf) {
                    logger.info("The first statement in the 'then statement' is an 'if statement'");
                    CtBlock<?> newBlock = modifyCondition(factory, statement, thenStatement.getStatement(0), dependableVariables, secretVariables);
                    newBlock.getStatements().forEach(element -> block.addStatement(element.clone()));
                    elseStatement = newBlock;
                } else {
                    handleStatementList(returnBlock, thenStatementsList);
                    elseStatement = block.insertEnd(thenStatementsList);
                }
                statement.setElseStatement(elseStatement);
            }
        } else {    // TODO define a new equals, that ignores the variables of assignment.
            logger.info("There is an else statement.");
            List<CtStatement> elseStatements = elseStatement.clone().getStatements();
            CtStatementList elseStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, elseStatements, statement, dependableVariables, secretVariables);

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

    private static boolean equals(CtBlock<?> thenBlock, CtBlock<?> elseBlock) {
        List<CtStatement> thenStatements = thenBlock.getStatements();
        List<CtStatement> elseStatements = elseBlock.getStatements();
        boolean equals = thenStatements.size() == elseStatements.size();

        Iterator<CtStatement> thenIterator = thenStatements.iterator();
        Iterator<CtStatement> elseIterator = elseStatements.iterator();

        while (thenIterator.hasNext() && elseIterator.hasNext()) {
            CtStatement thenStatement = thenIterator.next();
            CtStatement elseStatement = elseIterator.next();

            Class<?> thenStatementClass = thenStatement.getClass();
            if (thenStatementClass.equals(elseStatement.getClass())) {
                EqualStatementsFunction<CtStatement, CtStatement> function = equalsFunctions.get(thenStatementClass);
                if (function != null) {
                    equals = function.apply(thenStatement, elseStatement);
                }
            }
        }

        return equals;
    }

    private static void handleStatementList(CtBlock<?> returnBlock, CtStatementList statementsList) {
        logger.info("Handling the statement list.");
        if (!statementsList.getStatements().isEmpty() && !(statementsList.getStatement(0) instanceof CtIf)) {
            statementsList.getStatements().forEach(element -> returnBlock.addStatement(element.clone()));
        }
    }

    private static CtBlock<?> modifyCondition(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Modifying the condition.");
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        CtExpression<Boolean> initialStatementCondition = initialStatement.getCondition();
        CtExpression<Boolean> condition = ifElement.getCondition();
        String initialStatementConditionTarget = findConditionTarget(initialStatementCondition);
        String conditionTarget = findConditionTarget(condition);

        if (condition instanceof CtInvocation) {
            logger.info("Condition is an invocation.");
            handleInvocationCondition(factory, initialStatement, ifElement, dependableVariables, secretVariables, ctBlock, (CtInvocation<Boolean>) condition, initialStatementConditionTarget, conditionTarget);
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
        } else if (handOperand instanceof CtFieldReadImpl) {
            CtFieldReadImpl<?> fieldRead = (CtFieldReadImpl<?>) handOperand;
            CtExpression<?> target = fieldRead.getTarget();
            dependableVariables.add(target.toString());
        } else if (!(handOperand instanceof CtLiteralImpl)) {
            dependableVariables.add(handOperand.toString());
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

    private static void handleInvocationCondition(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables, List<CtVariable<?>> secretVariables, CtBlockImpl<?> ctBlock, CtInvocation<Boolean> conditionInvocation, String initialStatementConditionTarget, String conditionTarget) {
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
                CtInvocation<?> ctInvocation = (CtInvocation<?>) invocation;
                CtTypeReference<?> type = ctInvocation.getType();
                CtLocalVariable<?> newVariable = createNewVariable(factory, ctBlock, ctInvocation, type);
                ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement("", newVariable.getSimpleName());
            } else if (invocation instanceof CtExecutableReference) {
                logger.info("Invocation is of the type CtExecutableReference.");
                handleExecutableReference(factory, initialStatement, ifElement, dependableVariables, secretVariables, ctBlock, (CtExecutableReference<?>) invocation);
            }
        });
    }

    private static void handleExecutableReference(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables, List<CtVariable<?>> secretVariables, CtBlockImpl<?> ctBlock, CtExecutableReference<?> executableReference) {
        logger.info("Handling a executable reference.");
        List<CtTypeReference<?>> executableParameters = executableReference.getParameters();
        List<CtExpression<?>> parametersVariable = new ArrayList<>(executableParameters.size());
        CtLocalVariable<?> localVariable = NamingConvention.getLocalVariable();
        CtTypeReference<?> declaringType = executableReference.getDeclaringType();

        for (int idx = 0; idx < executableParameters.size(); idx++) {
            CtLocalVariable<?> newVariable = createNewVariable(factory, ctBlock, localVariable.getDefaultExpression(), declaringType);
            parametersVariable.add(factory.createCodeSnippetExpression(newVariable.getSimpleName()));
        }

        String type = executableReference.getType().getSimpleName();
        if (type.equalsIgnoreCase("boolean")) {
            CtIf anIf = factory.createIf();
            CtCodeSnippetExpression<Object> target = factory.createCodeSnippetExpression(localVariable.getSimpleName());
            CtInvocation invocation = factory.createInvocation(target, executableReference, parametersVariable);
            anIf.setCondition(invocation);

            CtBlock<?> thenStatement = ifElement.getThenStatement();
            CtBlock<?> elseStatement = ifElement.getElseStatement();

            CtBlock<?> newThenBlock = createNewBlock(thenStatement, factory, initialStatement, dependableVariables, secretVariables);
            anIf.setThenStatement(newThenBlock);

            if (elseStatement != null) {
                CtBlock<?> newElseBlock = createNewBlock(elseStatement, factory, initialStatement, dependableVariables, secretVariables);
                anIf.setElseStatement(newElseBlock);
            }
            ctBlock.addStatement(anIf);
        }
    }

    private static CtLocalVariable<?> createNewVariable(Factory factory, CtBlockImpl<?> ctBlock, CtExpression<?> oldVariable, CtTypeReference<?> declaringType) {
        logger.info("Creating a new variable.");
        CtLocalVariable<?> newVariable = NamingConvention.produceNewVariable(factory, declaringType, oldVariable);
        ctBlock.addStatement(newVariable);
        return newVariable;
    }

    private static CtBlock<?> createNewBlock( CtBlock<?> statement, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Creating a new block.");
        CtStatementList newElement = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, statement.clone().getStatements(), initialStatement, dependableVariables, secretVariables);
        CtBlock<?> newBlock = factory.createBlock();
        newElement.forEach(newThenStatement -> newBlock.addStatement(newThenStatement.clone()));
        return newBlock;
    }

    static CtBlock<?> modifyIf(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Modifying an 'if'.");
        CtIfImpl ifElement = (CtIfImpl) element;
        CtBlock<?> block = handleVulnerability(factory, ifElement, dependableVariables, secretVariables);

        if (!block.getStatements().isEmpty()) {
            initialStatement.setElseStatement(ifElement);
        }
        return block;
    }
}