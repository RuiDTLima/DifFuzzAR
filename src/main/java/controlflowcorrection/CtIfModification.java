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
import util.NamingConvention;
import java.util.*;
import java.util.stream.Collectors;

class CtIfModification {
    private static final Logger logger = LoggerFactory.getLogger(CtIfModification.class);

    /**
     * This method is invoked when an 'if' statement is found. If the 'if' condition uses a secret then the whole 'if'
     * statement will be modified. Otherwise the 'then' and 'else' blocks will be analysed in search of a possible source
     * of the vulnerability.
     * @param statement 		The 'if' statement that possibly contains the vulnerability.
     * @param factory   		The factory used to create new instructions.
     * @param secretVariables   A list of secret variables.
     * @param publicArguments   The list of public arguments.
     * @return  				Return an array of 'blocks' where in the first index is the received 'if' statement with
     * 							some slight modifications and in the second index is a new 'if' statement although with
     * 							the same condition.
     */
    static CtBlock<?>[] traverseStatement(CtStatement statement,
                                          Factory factory,
                                          List<CtVariable<?>> secretVariables,
                                          List<CtParameter<?>> publicArguments) {
        logger.info("Found an 'if' while traversing the method.");
        CtIfImpl ifStatement = (CtIfImpl) statement;

        if (ControlFlowBasedVulnerabilityCorrection.usesSecret(ifStatement.getCondition().toString(), secretVariables)) {
            logger.info("Found a source of vulnerability.");
            Set<String> dependableVariables = new HashSet<>();
            return handleVulnerability(factory, ifStatement, dependableVariables, secretVariables);
        }

        return handleIfBlock(factory, secretVariables, publicArguments, ifStatement);
    }

    /**
     * This method is where a supposedly vulnerable 'if' statement is handle in an attempt to make it safe. An 'if'
     * statement is assumed vulnerable if the condition uses a secret variable. If so then, if the statement has no 'else'
     * block, then the 'then' block is modified and placed as the 'else' block. If, there is a 'else' statement then a
     * modified version of the 'then' block is added to the 'else' block, and a modified version of the 'else' block is
     * added to the 'then' block. The modified versions versions added, need to ensure that the statement don't use any of
     * the secret variables.
     * @param factory    			The factory used to create new instructions.
     * @param statement    			The 'if' statement to be modified.
     * @param dependableVariables	A set of the dependable variables. Meaning all the variables that are in the 'if'
     *                              condition.
     * @param secretVariables    	A list of secret variables.
     * @return						Returns an array of 'blocks' where in the first index is the 'if' statement received
     * 								with the 'then' and 'else' blocks modified. In the second index is the modified
     * 								instructions of the 'else' block.
     */
    private static CtBlock<?>[] handleVulnerability(Factory factory,
                                                    CtIfImpl statement,
                                                    Set<String> dependableVariables,
                                                    List<CtVariable<?>> secretVariables) {
        logger.info("Handling the vulnerability");
        CtIf oldIf = statement.clone();
        CtIf newIf = factory.createIf();
        CtExpression<Boolean> condition = statement.getCondition();

        Set<String> newDependableVariables = new HashSet<>(dependableVariables);

        CtBlock<?> oldBlock = new CtBlockImpl<>();
        CtBlock<?> newBlock = new CtBlockImpl<>();
        CtBlock<?> elseBlock = new CtBlockImpl<>();

        addNewCondition(factory, newIf, condition);
        populateDependableVariables(newDependableVariables, condition);

        CtBlock<?> thenStatement = statement.getThenStatement();
        CtBlock<?> elseStatement = statement.getElseStatement();

        List<CtStatement> thenStatements = thenStatement.clone().getStatements();

        // index 0 -> old statement to keep in then block; index 1 -> new statements to add to else block
        CtStatementList[] thenStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, thenStatements, statement, newDependableVariables, secretVariables);
        CtStatementList oldThenStatementList = thenStatementsList[0];
        CtStatementList newThenStatementList = thenStatementsList[1];

        oldBlock.insertBegin(oldThenStatementList.clone());

        if (elseStatement != null) {
            List<CtStatement> elseStatements = elseStatement.clone().getStatements();
            CtStatementList[] elseStatementsList = ControlFlowBasedVulnerabilityCorrection.modifyStatements(factory, elseStatements, statement, newDependableVariables, secretVariables);
            CtStatementList oldElseStatementList = elseStatementsList[0];
            CtStatementList newElseStatementList = elseStatementsList[1];

            oldBlock.insertBegin(newElseStatementList.clone());
            newBlock.insertBegin(newElseStatementList.clone());
            elseBlock.insertBegin(oldElseStatementList.clone());
            elseBlock.insertBegin(newThenStatementList.clone());
            oldIf.setElseStatement(elseBlock);
        } else {
            if (oldThenStatementList.getStatements().size() != 0) {
                logger.info("There is no else statement.");
                if (oldThenStatementList.getStatement(0) instanceof CtIf) {
                    elseBlock = modifyCondition(factory, statement, oldThenStatementList.getStatement(0), newDependableVariables, secretVariables);
                    oldIf.setElseStatement(elseBlock);
                }
            }
            if (newThenStatementList.getStatements().size() != 0) {
                elseBlock.insertBegin(newThenStatementList.clone());
                oldIf.setElseStatement(elseBlock);
                newBlock.insertBegin(newThenStatementList.clone());
                newIf.setThenStatement(newBlock);
                newIf.setElseStatement(newBlock);
            }
        }

        oldIf.setThenStatement(oldBlock);

        CtBlock<?> oldIfBlock = factory.createBlock().addStatement(oldIf);
        CtBlock<?> newIfBlock = factory.createBlock();

        CtExpression<Boolean> newIfCondition = newIf.getCondition();

        if (!isUsingDependable(dependableVariables, newIfCondition)) {
            newIfBlock.addStatement(newIf);
        } else if (newBlock.getStatements().size() > 0 && newBlock.getStatement(0) instanceof CtLocalVariable){
            newIfBlock = newBlock;
        }

        return new CtBlock[] {oldIfBlock, newIfBlock};
    }

    /**
     * The method used to discover if the 'newIfCondition' is using any dependable variable, meaning a variable already
     * used in an outer 'if' statement.
     * @param dependableVariables	A set of the dependable variables. Meaning all the variables that are in the 'if'
     * 	 *                          condition.
     * @param newIfCondition		The 'if' condition under analysis.
     * @return						Returns true if the condition under analysis uses a dependable variable. False
     * 								otherwise.
     */
    private static boolean isUsingDependable(Set<String> dependableVariables, CtExpression<?> newIfCondition) {
        if (newIfCondition instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) newIfCondition;
            List<CtExpression<?>> toEvaluate = new ArrayList<>(invocation.getArguments());
            toEvaluate.add(invocation.getTarget());

            return toEvaluate
                    .stream()
                    .anyMatch(expression -> isUsingDependable(dependableVariables, expression));
        } else if (newIfCondition instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) newIfCondition;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

            return isUsingDependable(dependableVariables, leftHandOperand)
                    || isUsingDependable(dependableVariables, rightHandOperand);
        } else if (newIfCondition instanceof CtFieldRead) {
            CtFieldRead<?> fieldRead = (CtFieldRead<?>) newIfCondition;
            CtExpression<?> target = fieldRead.getTarget();
            if (target instanceof CtVariableRead) {
                return dependableVariables.contains(target.toString());
            }
        }
        return dependableVariables.contains(newIfCondition.toString());
    }

    /**
     * The method responsible for modifying the condition of the 'if' statement.
     * @param factory	The factory used to create new instructions.
     * @param ctIf		The 'if' statement to have its condition modified.
     * @param condition	The 'if' condition under analysis.
     */
    private static void addNewCondition(Factory factory, CtIf ctIf, CtExpression<?> condition) {
        if (condition instanceof CtBinaryOperator) {
            logger.info("Condition is a binary operator.");
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) condition;
            CtExpression leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression rightHandOperand = binaryOperator.getRightHandOperand();

            CtExpression<Boolean> newLeftHandOperand = evaluateHandOperand(factory, ctIf, leftHandOperand);
            CtExpression<Boolean> newRightHandOperand = evaluateHandOperand(factory, ctIf,rightHandOperand);

            CtBinaryOperator<Boolean> newBinaryOperator = factory.createBinaryOperator(newLeftHandOperand, newRightHandOperand, binaryOperator.getKind());
            ctIf.setCondition(newBinaryOperator);
            return;
        } else if (condition instanceof CtInvocation) {
            logger.info("Condition is an invocation.");
            CtInvocation<?> invocation = (CtInvocation<?>) condition;
            CtExpression<?> target = invocation.getTarget();

            if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(target.toString())) {
                String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(target.toString());
                CtInvocation newInvocation = invocation.clone();
                newInvocation.setTarget(factory.createCodeSnippetExpression(replacement));
                ctIf.setCondition(newInvocation);
                return;
            }
        } else if (condition instanceof CtUnaryOperator) {
            CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) condition;
            CtExpression<?> operand = unaryOperator.getOperand();
            addNewCondition(factory, ctIf, operand);
            return;
        } else if (!(condition instanceof CtLiteralImpl) && !(condition instanceof CtTypeAccessImpl)) {
            if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(condition.toString())) {
                String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(condition.toString());
                ctIf.setCondition(factory.createCodeSnippetExpression(replacement));
                return;
            }
        }
        ctIf.setCondition((CtExpression<Boolean>) condition);
    }

    /**
     * The method responsible for retrieving the variable used in the expression.
     * @param factory		The factory used to create new instructions.
     * @param ctIf			The 'if' statement where the expression is used as a condition.
     * @param expression	The expression to be evaluated in order to retrieve the variable used.
     * @return				Returns the variable used in the expression
     */
    private static CtExpression<Boolean> evaluateHandOperand(Factory factory, CtIf ctIf, CtExpression<Boolean> expression) {
        String evaluateOperand = null;
        if (expression instanceof CtInvocation) {
            logger.info("Hand operand is an invocation.");
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            CtExpression<?> target = invocation.getTarget();
            evaluateOperand = target.toString();
        } else if (expression instanceof CtBinaryOperator) {
            logger.info("Hand operand is a binary operator.");
            addNewCondition(factory, ctIf, expression);
        } else if (expression instanceof CtFieldReadImpl) {
            CtFieldReadImpl<?> fieldRead = (CtFieldReadImpl<?>) expression;
            CtExpression<?> target = fieldRead.getTarget();
            evaluateOperand = target.toString();
        } else if (!(expression instanceof CtLiteralImpl)) {
            evaluateOperand = expression.toString();
        }

        if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(evaluateOperand)) {
            String replacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(evaluateOperand);
            return factory.createCodeSnippetExpression(replacement);
        }
        return expression;
    }

    /**
     * A method where the variables used in the 'if' condition are added to the list of dependable variables. Meaning
     * that any instruction that uses any of those variables cannot leave its block.
     * @param dependableVariables	The set of dependable variables.
     * @param expression    		The condition of the 'if' statement.
     */
    private static void populateDependableVariables(Set<String> dependableVariables, CtExpression<?> expression) {
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

    /**
     * The method where each hand operand is process in order to add more variables to the set of dependable variables.
     * @param dependableVariables	The set of dependable variables.
     * @param handOperand			The hand operand to be processed.
     */
    private static void handleHandOperand(Set<String> dependableVariables, CtExpression<?> handOperand) {
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

    /**
     * The method where the modification of the condition starts, depending on the type of the condition. As it stands
     * the condition is only modified if it is an invocation.
     * @param factory				The factory used to create new instructions.
     * @param initialStatement		The initial 'if' statement that the current statement belongs to.
     * @param ifStatement			The current 'if' statement being modified.
     * @param dependableVariables	The set of dependable variables.
     * @param secretVariables		A list of secret variables.
     * @return						Returns the new block containing the modified condition.
     */
    private static CtBlock<?> modifyCondition(Factory factory,
                                              CtIfImpl initialStatement,
                                              CtIf ifStatement,
                                              Set<String> dependableVariables,
                                              List<CtVariable<?>> secretVariables) {
        logger.info("Modifying the condition.");
        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        CtExpression<Boolean> condition = ifStatement.getCondition();

        if (condition instanceof CtInvocation) {
            logger.info("Condition is an invocation.");
            CtInvocation<Boolean> invocation = (CtInvocation<Boolean>) condition;
            ctBlock = handleInvocationCondition(factory, initialStatement, ifStatement, dependableVariables, secretVariables, invocation);
        }
        return ctBlock;
    }

    /**
     * The method where an invocation that is a condition changes to be a block of instructions to represent the condition.
     * Meaning, to have the same as close as possible the same operations.
     * @param factory				The factory used to create new instructions.
     * @param initialStatement		The initial statement that the 'if' with an invocation condition belongs to.
     * @param ifStatement			The 'if' statement with an invocation condition.
     * @param dependableVariables	The set of dependable variables.
     * @param secretVariables		A list of secret variables.
     * @param conditionInvocation	The 'if' condition that is an invocation.
     * @return						Returns a block with the statements to represent the condition.
     */
    private static CtBlockImpl<?> handleInvocationCondition(Factory factory,
                                                            CtIfImpl initialStatement,
                                                            CtIf ifStatement,
                                                            Set<String> dependableVariables,
                                                            List<CtVariable<?>> secretVariables,
                                                            CtInvocation<Boolean> conditionInvocation) {

        logger.info("Handling a condition that is an invocation.");
        List<CtExpression<?>> conditionInvocationArguments = conditionInvocation.getArguments();

        CtExpression<Boolean> initialStatementCondition = initialStatement.getCondition();
        String initialStatementConditionTarget = findConditionTarget(initialStatementCondition);
        String conditionTarget = findConditionTarget(conditionInvocation);

        CtBlockImpl<?> ctBlock = new CtBlockImpl<>();
        List<CtElement> invocations = new ArrayList<>();

        conditionInvocationArguments
                .stream()
                .filter(argument -> argument instanceof CtInvocation)
                .map(argument -> (CtInvocation<?>)argument)
                .filter(invocation -> !invocation.getExecutable().getSimpleName().equals(initialStatementConditionTarget))
                .forEach(invocations::add);

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
                CtExecutableReference<?> executableReference = (CtExecutableReference<?>) invocation;
                handleExecutableReference(factory, initialStatement, ifStatement, dependableVariables, secretVariables, ctBlock, executableReference);
            }
        });
        return ctBlock;
    }

    /**
     * The method where the target of the invocation in the condition happens.
     * @param invocationCondition	The invocation condition from which to retrieve the target.
     * @return						Returns a String that represents the target of the invocation.
     */
    private static String findConditionTarget(CtExpression<Boolean> invocationCondition) {
        logger.info("Finding the target of the condition.");
        CtExpression<?> possibleTarget = invocationCondition;
        while (possibleTarget instanceof CtInvocation) {
            possibleTarget = ((CtInvocation<?>) possibleTarget).getTarget();
        }
        return possibleTarget.toString();
    }

    /**
     * The method where an executable reference is altered to fit a new usage. In this case, only if its return type is
     * boolean will it be modified to create a new if where the condition is the invocation of the executable and the
     * 'then' block is a modified version of the 'then' block in 'ifElement' and the 'else' block is modified version of
     * the 'else' block in 'ifElement'.
     * @param factory				The factory used to create new instructions.
     * @param initialStatement		The initial 'if' statement that originated this modification.
     * @param ifElement				The 'if' element where the executable reference is used in the condition.
     * @param dependableVariables	A set containing all the dependable variables.
     * @param secretVariables 		A list of secret variables.
     * @param ctBlock				The block where the modified instruction will be added.
     * @param executableReference	The executable reference to be modified.
     */
    private static void handleExecutableReference(Factory factory,
                                                  CtIfImpl initialStatement,
                                                  CtIf ifElement,
                                                  Set<String> dependableVariables,
                                                  List<CtVariable<?>> secretVariables,
                                                  CtBlockImpl<?> ctBlock,
                                                  CtExecutableReference<?> executableReference) {
        logger.info("Handling a executable reference.");
        List<CtTypeReference<?>> executableParameters = executableReference.getParameters();
        CtLocalVariable<?> localVariable = NamingConvention.getLocalVariable();
        CtTypeReference<?> declaringType = executableReference.getDeclaringType();

        List<CtExpression<?>> parametersVariable = executableParameters
                .stream()
                .map( parameter -> {
                    CtLocalVariable<?> newVariable = createNewVariable(factory, ctBlock, localVariable.getDefaultExpression(), declaringType);
                    return factory.createCodeSnippetExpression(newVariable.getSimpleName());
                })
                .collect(Collectors.toList());

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

    /**
     * The method where a new variable is create while also being added to the block in construction.
     * @param factory		The factory used to create new instructions.
     * @param ctBlock		The block in construction to which the new variable will be added.
     * @param defaultValue	The value to be assigned to the new variable.
     * @param declaringType	The type of the new variable.
     * @return				The new variable created.
     */
    private static CtLocalVariable<?> createNewVariable(Factory factory,
                                                        CtBlockImpl<?> ctBlock,
                                                        CtExpression<?> defaultValue,
                                                        CtTypeReference<?> declaringType) {
        logger.info("Creating a new variable.");
        CtLocalVariable<?> newVariable = NamingConvention.produceNewVariable(factory, declaringType, defaultValue);
        ctBlock.addStatement(newVariable);
        return newVariable;
    }

    /**
     * The method where a block is created with all the modified statements from the block received.
     * @param block					The block containing all the statements to be modified.
     * @param factory				The factory used to create new instructions.
     * @param initialStatement		The initial 'if' statement that originated this modification.
     * @param dependableVariables	A set of the dependable variables.
     * @param secretVariables		A list of the secret variables.
     * @return						A new block containing a modified version of the statements in 'block'.
     */
    private static CtBlock<?> createNewBlock(CtBlock<?> block,
                                             Factory factory,
                                             CtIfImpl initialStatement,
                                             Set<String> dependableVariables,
                                             List<CtVariable<?>> secretVariables) {
        logger.info("Creating a new block.");
        List<CtStatement> statements = block.clone().getStatements();
        CtStatementList[] newElement = ControlFlowBasedVulnerabilityCorrection
                .modifyStatements(factory, statements, initialStatement, dependableVariables, secretVariables);

        CtStatementList newElementNewIf = newElement[1];

        CtBlock<?> newBlock = factory.createBlock();
        newElementNewIf.forEach(newThenStatement -> newBlock.addStatement(newThenStatement.clone()));
        return newBlock;
    }

    /**
     * The statement that handles the 'if' statement where the condition does not depends on any secret. Each statement
     * of the 'then' and 'else' blocks will be analysed in search for a possible source of the vulnerability. And two new
     * blocks of 'if' statements will be produced.
     * @param factory			The factory used to create new instructions.
     * @param secretVariables	A list of secret variables.
     * @param publicArguments	The list of public arguments.
     * @param ifStatement		The 'if' statement to be analysed.
     * @return					Returns an array of 'CtBlock' where in the first index is the received 'if' statement
     * 							with slight modifications and in the second index is a new 'if' statement where it only
     * 							maintains the condition.
     */
    private static CtBlock<?>[] handleIfBlock(Factory factory,
                                              List<CtVariable<?>> secretVariables,
                                              List<CtParameter<?>> publicArguments,
                                              CtIfImpl ifStatement) {
        logger.info("The current if condition does not depend on a secret. So the body of the 'then' and 'else' must be analysed.");
        CtBlock<?> thenBlock = ifStatement.getThenStatement();
        CtBlock<?> elseBlock = ifStatement.getElseStatement();

        CtIfImpl oldIf = new CtIfImpl();
        CtIfImpl newIf = new CtIfImpl();

        oldIf.setCondition(ifStatement.getCondition());
        newIf.setCondition(ifStatement.getCondition());

        CtBlock<?>[] returnBlocks = new CtBlock[2];
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
        return returnBlocks;
    }

    /**
     * The method invoked when an 'if' statement is found when analysing instructions to modify since it can cause the
     * vulnerability.
     * @param element				The 'if' element to be modified.
     * @param factory				The factory used to create new instructions.
     * @param initialStatement		The initial 'if' statement that originated the modification. NOT USED.
     * @param dependableVariables	A set containing the dependable variables.
     * @param secretVariables		A list of secret variables.
     * @return 						Returns an array of 'blocks' where in the first index is the 'if' statement received
     * 								with the 'then' and 'else' blocks modified. In the second index is the modified
     * 								instructions of the 'else' block.
     */
    static CtBlock<?>[] modifyIf(CtElement element,
                                 Factory factory,
                                 CtIfImpl initialStatement,
                                 Set<String> dependableVariables,
                                 List<CtVariable<?>> secretVariables) {
        logger.info("Modifying an 'if'.");
        CtIfImpl ifElement = (CtIfImpl) element;
        return handleVulnerability(factory, ifElement, dependableVariables, secretVariables);
    }
}