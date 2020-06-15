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
        CtIfImpl ifStatement = (CtIfImpl) statement;
        if (ControlFlowBasedVulnerabilityCorrection.usesSecret(ifStatement.getCondition().toString(), secretVariables)) {
            logger.info("Found the source of vulnerability.");
            List<String> dependableVariables = new ArrayList<>();
            handleVulnerability(factory, ifStatement, dependableVariables);
        } else {
            CtBlock<?> thenBlock = ifStatement.getThenStatement();
            CtBlock<?> elseBlock = ifStatement.getElseStatement();
            ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, thenBlock, secretVariables, publicArguments);
            if (elseBlock != null)
                ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, elseBlock, secretVariables, publicArguments);
        }
    }

    private static CtBlock<?> handleVulnerability(Factory factory, CtIfImpl statement, List<String> dependableVariables) {
        CtExpression<Boolean> condition = statement.getCondition();

        populateDependableVariables(dependableVariables, condition);

        CtBlock<?> returnBlock = new CtBlockImpl<>();

        CtBlock<?> thenStatement = statement.getThenStatement();
        CtBlock<?> elseStatement = statement.getElseStatement();

        List<CtStatement> thenStatements = thenStatement.clone().getStatements();

        if (elseStatement == null) {
            CtBlock<Object> block = factory.createBlock();
            if (thenStatement.getStatement(0) instanceof CtIf) {
                CtBlock<?> newBlock = modifyCondition(factory, statement, thenStatement.getStatement(0), dependableVariables);
                newBlock.getStatements().forEach(element -> block.addStatement(element.clone()));
                elseStatement = newBlock;
            } else {
                CtStatementList thenStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatements, statement, dependableVariables);
                if (!thenStatementsList.getStatements().isEmpty() && !(thenStatementsList.getStatement(0) instanceof CtIf)) {
                    thenStatementsList.getStatements().forEach(element -> returnBlock.addStatement(element.clone()));
                }
                elseStatement = block.insertEnd(thenStatementsList);
            }
            statement.setElseStatement(elseStatement);
        } else {
            // TODO review if this code section can be placed before the 'if'.
            CtStatementList thenStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatements, statement, dependableVariables);
            if (!thenStatementsList.getStatements().isEmpty() && !(thenStatementsList.getStatement(0) instanceof CtIf)) {
                thenStatementsList.getStatements().forEach(element -> returnBlock.addStatement(element.clone()));
            }
            List<CtStatement> elseStatements = elseStatement.clone().getStatements();
            CtStatementList elseStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, elseStatements, statement, dependableVariables);
            if (!elseStatementsList.getStatements().isEmpty() && !(elseStatementsList.getStatement(0) instanceof CtIf)) {
                elseStatementsList.getStatements().forEach(element -> returnBlock.addStatement(element.clone()));
            }
            thenStatement.insertBegin(elseStatementsList);
            elseStatement.insertBegin(thenStatementsList);
        }

        return returnBlock;
    }

    private static void populateDependableVariables(List<String> dependableVariables, CtExpression<?> condition) {
        if (condition instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) condition;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();
            if (leftHandOperand instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) leftHandOperand;
                CtExpression<?> target = invocation.getTarget();
                dependableVariables.add(target.toString());
            } else if (leftHandOperand instanceof CtBinaryOperator) {
                populateDependableVariables(dependableVariables, leftHandOperand);
            }
            if (rightHandOperand instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) rightHandOperand;
                CtExpression<?> target = invocation.getTarget();
                dependableVariables.add(target.toString());
            } else if (rightHandOperand instanceof CtBinaryOperator) {
                populateDependableVariables(dependableVariables, rightHandOperand);
            }
        } else if (condition instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) condition;
            CtExpression<?> target = invocation.getTarget();
            dependableVariables.add(target.toString());
        } else {
            dependableVariables.add(condition.toString());
        }
    }

    private static CtBlock<?> modifyCondition(Factory factory, CtIfImpl initialStatement, CtIf ifElement, List<String> dependableVariables) {
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        CtExpression<Boolean> initialStatementCondition = initialStatement.getCondition();
        CtExpression<Boolean> condition = ifElement.getCondition();
        String initialStatementConditionTarget = findConditionTarget(initialStatementCondition);
        String conditionTarget = findConditionTarget(condition);

        if (condition instanceof CtInvocation) {
            CtInvocation<Boolean> conditionInvocation = (CtInvocation<Boolean>) condition;
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
                    String type = ((CtInvocation<?>) invocation).getType().toString();
                    int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
                    String newVariable = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter;
                    CtCodeSnippetExpression<Object> expression = factory.createCodeSnippetExpression(newVariable);
                    ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement("", expression.toString());
                    CtCodeSnippetStatement codeSnippetStatement = factory.createCodeSnippetStatement(type + " " + newVariable + " = " + invocation);
                    ctBlock.addStatement(codeSnippetStatement);
                } else if (invocation instanceof CtExecutableReference) {
                    CtExecutableReference<?> executableReference = (CtExecutableReference<?>) invocation;
                    List<CtTypeReference<?>> executableParameters = executableReference.getParameters();
                    List<String> parametersVariable = new ArrayList<>(executableParameters.size());
                    String oldVariable = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + ControlFlowBasedVulnerabilityCorrection.getCounter();
                    String declaringType = executableReference.getDeclaringType().toString();
                    for (int idx = 0; idx < executableParameters.size(); idx++) {
                        int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
                        String newVariable = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter;
                        CtCodeSnippetStatement variableInitiation = factory.createCodeSnippetStatement(declaringType + " " + newVariable + " = " + oldVariable);
                        ctBlock.addStatement(variableInitiation);
                        parametersVariable.add(newVariable);
                    }
                    String type = executableReference.getType().toString();
                    if (type.equalsIgnoreCase("boolean")) {
                        CtIf anIf = factory.createIf();
                        CtCodeSnippetExpression<Boolean> codeSnippetStatement = factory.createCodeSnippetExpression(oldVariable + "." + executableReference.getSimpleName() + "(" + String.join(" ,", parametersVariable) + ")");
                        anIf.setCondition(codeSnippetStatement);

                        CtBlock<?> thenStatement = ifElement.getThenStatement();
                        CtBlock<?> elseStatement = ifElement.getElseStatement();
                        CtStatementList newThenElement = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatement.clone().getStatements(), initialStatement, dependableVariables);
                        CtBlock<?> newThenBlock = factory.createBlock();
                        newThenElement.forEach(newThenStatement -> newThenBlock.addStatement(newThenStatement.clone()));
                        anIf.setThenStatement(newThenBlock);
                        if (elseStatement != null) {
                            CtBlock<?> newElseBlock = factory.createBlock();
                            CtStatementList newElseElement = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, elseStatement.clone().getStatements(), initialStatement, dependableVariables);
                            newElseElement.forEach(newElseStatement -> newElseBlock.addStatement(newElseStatement.clone()));
                            anIf.setElseStatement(newElseBlock);
                        }

                        ctBlock.addStatement(anIf);
                    }
                }
            });
        }
        return ctBlock;
    }

    private static String findConditionTarget(CtExpression<Boolean> initialStatementCondition) {
        CtExpression<?> possibleTarget = initialStatementCondition;
        while (possibleTarget instanceof CtInvocation) {
            possibleTarget = ((CtInvocation<?>) possibleTarget).getTarget();
        }
        return possibleTarget.toString();
    }

    static void modifyIf(Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, CtStatementList statementList, CtIfImpl ifElement) {
        CtBlock<?> block = handleVulnerability(factory, ifElement, dependableVariables);   // TODO remove from secretVariables the initialization of the variable
        block.getStatements().forEach(statementList::addStatement);

        if (!block.getStatements().isEmpty()) {
            initialStatement.setElseStatement(ifElement);
        }
    }

}
