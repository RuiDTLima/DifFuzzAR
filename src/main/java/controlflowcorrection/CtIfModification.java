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

    static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found an 'if' while traversing the method.");
        CtIfImpl ifStatement = (CtIfImpl) statement;
        CtBlock<?>[] returnBlocks;

        if (ControlFlowBasedVulnerabilityCorrection.usesSecret(ifStatement.getCondition().toString(), secretVariables)) {
            logger.info("Found a source of vulnerability.");
            List<String> dependableVariables = new ArrayList<>();
            returnBlocks = handleVulnerability(factory, ifStatement, dependableVariables, secretVariables);
        } else {
            logger.info("The current if condition does not depend on a secret. So the body of the 'then' and 'else' must be analysed.");
            CtBlock<?> thenBlock = ifStatement.getThenStatement();
            CtBlock<?> elseBlock = ifStatement.getElseStatement();

            CtIfImpl oldIf = new CtIfImpl();
            CtIfImpl newIf = new CtIfImpl();

            oldIf.setCondition(ifStatement.getCondition());
            newIf.setCondition(ifStatement.getCondition());

            returnBlocks = new CtBlock[2];
            returnBlocks[0] = new CtBlockImpl<>();
            returnBlocks[1] = new CtBlockImpl<>();

            CtBlock<?>[] returnedThenBlock = ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, thenBlock, secretVariables, publicArguments);
            oldIf.setThenStatement(returnedThenBlock[0]);
            newIf.setThenStatement(returnedThenBlock[1]);
            if (elseBlock != null) {
                CtBlock<?>[] returnedElseBlock = ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, elseBlock, secretVariables, publicArguments);
                oldIf.setElseStatement(returnedElseBlock[0]);
                newIf.setElseStatement(returnedElseBlock[1]);
            } else {
                newIf.setElseStatement(returnedThenBlock[1]);
            }
            returnBlocks[0].addStatement(oldIf);
            returnBlocks[1].addStatement(newIf);
        }
        return returnBlocks;
    }

    private static CtBlock<?>[] handleVulnerability(Factory factory, CtIfImpl statement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Handling the vulnerability");
        CtIf oldIf = statement.clone();
        CtIf newIf = new CtIfImpl();
        CtExpression<Boolean> condition = statement.getCondition();

        CtBlock<?> oldBlock = new CtBlockImpl<>();
        CtBlock<?> newBlock = new CtBlockImpl<>();
        CtBlock<?> elseBlock = new CtBlockImpl<>();

        populateDependableVariables(dependableVariables, condition);

        CtBlock<?> thenStatement = statement.getThenStatement();
        CtBlock<?> elseStatement = statement.getElseStatement();

        List<CtStatement> thenStatements = thenStatement.clone().getStatements();
        // index 0 -> old statement to keep in then block; index 1 -> new statements to add to else block
        CtStatementList[] thenStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatements, statement, dependableVariables, secretVariables);
        CtStatementList oldThenStatementList = thenStatementsList[0];
        CtStatementList newThenStatementList = thenStatementsList[1];

        oldBlock.insertBegin(oldThenStatementList.clone());

        if (elseStatement != null) {
            List<CtStatement> elseStatements = elseStatement.clone().getStatements();
            CtStatementList[] elseStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, elseStatements, statement, dependableVariables, secretVariables);
            CtStatementList oldElseStatementList = elseStatementsList[0];
            CtStatementList newElseStatementList = elseStatementsList[1];
            CtStatementList newElse = newElseStatementList.clone();

            oldBlock.insertBegin(newElse.clone());
            elseBlock.insertBegin(oldElseStatementList.clone());
            elseBlock.insertBegin(newThenStatementList.clone());
            newBlock.insertBegin(newElse.clone());
            oldIf.setElseStatement(elseBlock);
        } else {
            if (oldThenStatementList.getStatements().size() != 0) {
                logger.info("There is no else statement.");
                if (oldThenStatementList.getStatement(0) instanceof CtIf) {
                    CtBlock<?> changedBlock = modifyCondition(factory, statement, oldThenStatementList.getStatement(0), dependableVariables, secretVariables);
                    oldIf.setElseStatement(changedBlock);
                }
            }
            if (newThenStatementList.getStatements().size() != 0) {
                elseBlock.insertBegin(newThenStatementList);
                oldIf.setElseStatement(elseBlock);
            }
        }

        oldIf.setThenStatement(oldBlock);

        CtBlock<?> oldIfBlock = factory.createBlock().addStatement(oldIf);
        CtBlock<?> newIfBlock = factory.createBlock();

        if (isConditionValid(dependableVariables, condition)) {
            newIf.setCondition(oldIf.getCondition());
            newIf.setThenStatement(newBlock);
            newIf.setElseStatement(newBlock);

            newIfBlock.addStatement(newIf);
        } else {
            List<CtStatement> statements = newBlock.getStatements();
            if (statements.size() != 0){
                statements.forEach(element -> newIfBlock.addStatement(element.clone()));
            }
        }

        return new CtBlock[] {oldIfBlock, newIfBlock};
    }

    private static boolean isConditionValid(List<String> dependableVariables, CtExpression<?> condition) {
        CtExpression<?> element = condition;
        if (condition instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) condition;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

            return isConditionValid(dependableVariables, leftHandOperand) &&
                    isConditionValid(dependableVariables, rightHandOperand);

        } else if (condition instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) condition;
            element = invocation.getTarget();
        } else if (condition instanceof CtFieldReadImpl) {
            CtFieldReadImpl<?> fieldRead = (CtFieldReadImpl<?>) condition;
            element = fieldRead.getTarget();
        } else if (condition instanceof CtArrayRead) {
            CtArrayReadImpl<?> arrayRead = (CtArrayReadImpl<?>) condition;
            element = arrayRead.getTarget();
        }

        return !dependableVariables.contains(element.toString());
    }

    private static void populateDependableVariables(List<String> dependableVariables, CtExpression<?> expression) {
        logger.info("Populating the dependable variables.");
        if (expression instanceof CtBinaryOperator) {
            logger.info("Condition is a binary operator.");
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) expression;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

            handleHandOperand(dependableVariables, leftHandOperand);
            handleHandOperand(dependableVariables, rightHandOperand);

        } else if (expression instanceof CtInvocation) {
            logger.info("Condition is an invocation.");
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            CtExpression<?> target = invocation.getTarget();
            dependableVariables.add(target.toString());
        } else if (!(expression instanceof CtLiteralImpl) && !(expression instanceof CtTypeAccessImpl)) {
            dependableVariables.add(expression.toString());
        }
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

    private static CtBlock<?> createNewBlock(CtBlock<?> statement, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Creating a new block.");
        CtStatementList[] newElement = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, statement.clone().getStatements(), initialStatement, dependableVariables, secretVariables);

        CtStatementList newElementOldIf = newElement[0];
        CtStatementList newElementNewIf = newElement[1];

       /* CtBlock<?> oldBlock = factory.createBlock();
        newElementOldIf.forEach(newThenStatement -> oldBlock.addStatement(newThenStatement.clone()));*/

        CtBlock<?> newBlock = factory.createBlock();
        newElementNewIf.forEach(newThenStatement -> newBlock.addStatement(newThenStatement.clone()));
        // return new CtBlock[]{oldBlock, newBlock};
        return newBlock;
    }

    static CtBlock<?>[] modifyIf(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Modifying an 'if'.");
        CtIfImpl ifElement = (CtIfImpl) element;
        return handleVulnerability(factory, ifElement, dependableVariables, secretVariables);
    }
}