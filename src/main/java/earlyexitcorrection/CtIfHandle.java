package earlyexitcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtArrayReadImpl;
import spoon.support.reflect.code.CtFieldReadImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtLiteralImpl;
import java.util.Iterator;

class CtIfHandle {
    private static final Logger logger = LoggerFactory.getLogger(CtIfHandle.class);

    /**
     * The method where an 'if' statement is handled. Here a new 'if' statement will be created with a modified version
     * of the original 'if' statement's condition. The 'then' and 'else' blocks will be a modified version of the original
     * 'if' statement 'then' and 'else' block.
     * @param factory           The factory used to create new instructions.
     * @param returnsIterator   An iterator over the returns of the method.
     * @param returnVariable    The variable to be returned.
     * @param returnElement     The valid return expression returned in the final return statement. Can't be a binary
     *                          operator nor an invocation that uses a variable.
     * @param afterWhileReturn  Indicates if this 'if' statement happens after a 'while' statement.
     * @param newBody           A block of statements that will become the new body of the vulnerable method.
     * @param currentStatement  The 'if' statement under analysis.
     * @return                  Indicates if the next statement will be after a 'while' cycle.
     */
    static boolean handleIf(Factory factory,
                            Iterator<CtCFlowBreak> returnsIterator,
                            CtLocalVariable<?> returnVariable,
                            CtExpression<?> returnElement,
                            boolean afterWhileReturn,
                            CtBlock<?> newBody,
                            CtStatement currentStatement) {

        logger.info("Handling an 'if' statement.");
        CtIfImpl ifStatement = (CtIfImpl) currentStatement;
        Iterator<CtStatement> thenStatements = ((CtBlock<?>) (ifStatement.getThenStatement())).iterator();
        CtBlock<?> elseStatements = ifStatement.getElseStatement();

        CtIf newIfStatement = factory.createIf();
        newIfStatement.setCondition(ifStatement.getCondition());
        modifyIfCondition(factory, newIfStatement);

        CtBlock<?> newThenBlock = EarlyExitVulnerabilityCorrection
                .handleStatements(factory, thenStatements, returnsIterator, returnVariable, returnElement, afterWhileReturn);

        newIfStatement.setThenStatement(newThenBlock);

        if (elseStatements != null) {
            Iterator<CtStatement> iterator = elseStatements.iterator();
            CtBlock<?> newElseBlock = EarlyExitVulnerabilityCorrection
                    .handleStatements(factory, iterator, returnsIterator, returnVariable, returnElement, afterWhileReturn);

            newIfStatement.setElseStatement(newElseBlock);
        }

        newBody.addStatement(newIfStatement);
        return false;
    }

    /**
     * Modifies the condition of the if. This is important when accessing an array, to avoid IndexOutOfBoundsException.
     * Since it is likely that the condition to avoid the IndexOutOfBoundsException no longer works to avoid early-exit
     * timing side-channel.
     * @param factory       The factory used to create code snippets to add.
     * @param ifStatement   The if statement to be modified.
     */
    static void modifyIfCondition(Factory factory, CtIf ifStatement) {
        logger.info("Modifying the 'if' condition.");
        if (ifStatement.getCondition() instanceof CtBinaryOperator) {
            CtBinaryOperator<Boolean> condition = (CtBinaryOperator<Boolean>) ifStatement.getCondition();
            CtExpression<?> leftHandOperator = condition.getLeftHandOperand();
            CtExpression<?> rightHandOperator = condition.getRightHandOperand();

            verifyNullCheck(leftHandOperator, rightHandOperator);
            verifyNullCheck(rightHandOperator, leftHandOperator);

            CtBinaryOperator<Boolean> newLeftHand = createNewHand(factory, leftHandOperator);
            CtBinaryOperator<Boolean> newRightHand = createNewHand(factory, rightHandOperator);

            if (newLeftHand != null || newRightHand != null) {
                CtBinaryOperator<Boolean> addedCondition;
                if (newLeftHand != null && newRightHand != null) {
                    addedCondition = factory.createBinaryOperator(newLeftHand, newRightHand, BinaryOperatorKind.AND);
                } else {
                    addedCondition = newLeftHand != null ? newLeftHand : newRightHand;
                }

                CtBinaryOperator<Boolean> newConditional = factory.createBinaryOperator(addedCondition, condition, BinaryOperatorKind.AND);
                ifStatement.setCondition(newConditional);
                logger.info("Defined new condition for the 'if'.");
            }
        }
    }

    /**
     * The method where a new binary operator is created if the original one is a field read operation or an array read
     * operation.
     * @param factory       The factory used to create new instructions.
     * @param handOperator  The original hand operator to be modified.
     * @return              The new binary operator that might include a null or length check to a variable in
     *                      'handOperator'.
     */
    private static CtBinaryOperator<Boolean> createNewHand(Factory factory, CtExpression<?> handOperator) {
        logger.info("Modifying the the hand operator.");
        CtBinaryOperator<Boolean> newHand = null;
        if (handOperator instanceof CtFieldReadImpl) {
            CtExpression<?> variable = ((CtFieldReadImpl<?>) handOperator).getTarget();
            newHand = EarlyExitVulnerabilityCorrection.addNullCheck(factory, variable);
        } else if (handOperator instanceof CtArrayReadImpl) {
            newHand = addLengthCheck(factory, (CtArrayReadImpl<?>) handOperator);
        }
        return newHand;
    }

    /**
     * The method to check if the variable in 'anotherHandOperator' is being compared to null. If so, that variable
     * name must be kept for future protection of an access to that variable.
     * @param handOperator          One of the hand operators in analysis. The one expected to be null.
     * @param anotherHandOperator   The other hand operator in analysis. The one expected to be compared to null.
     */
    private static void verifyNullCheck(CtElement handOperator, CtElement anotherHandOperator) {
        logger.info("Verifying if a null check is done to the element {}.", handOperator.prettyprint());
        if (handOperator instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) handOperator;
            CtElement leftElement = binaryOperator.getLeftHandOperand();
            CtElement rightElement = binaryOperator.getRightHandOperand();
            verifyNullCheck(leftElement, rightElement);
            verifyNullCheck(rightElement, leftElement);
        } else if (handOperator instanceof CtLiteralImpl && ((CtLiteralImpl<?>)handOperator).getValue() == null) {
            EarlyExitVulnerabilityCorrection.addVariableNullChecked(anotherHandOperator.toString());
        }
    }

    /**
     * The method where a length check will be added to protect the access of the array in 'arrayRead'. If needed a null
     * check will also be added.
     * @param factory   The factory used to create new instructions.
     * @param arrayRead The array read to protect.
     * @return          Returns a new binary operator to be used as protection for the access of the 'arrayRead'.
     */
    private static CtBinaryOperator<Boolean> addLengthCheck(Factory factory, CtArrayReadImpl<?> arrayRead) {
        logger.info("Adding a length check to and array read.");
        CtExpression<?> target = arrayRead.getTarget();
        CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();
        if (indexExpression instanceof CtUnaryOperator) {
            logger.info("Modify the expression {}.", indexExpression);
            indexExpression = ((CtUnaryOperator<Integer>) indexExpression).getOperand();
        }

        CtFieldReference<Integer> fieldReference = factory.createFieldReference();
        CtTypeReference<Integer> typeReference = factory.Type().INTEGER;
        fieldReference.setDeclaringType(typeReference);
        fieldReference.setSimpleName("length");
        fieldReference.setType(typeReference);

        CtFieldRead<Integer> fieldRead = factory.createFieldRead();
        fieldRead.setTarget(target);
        fieldRead.setVariable(fieldReference);
        CtBinaryOperator<Boolean> lengthCheck = factory.createBinaryOperator(indexExpression, fieldRead, BinaryOperatorKind.LT);
        CtBinaryOperator<Boolean> nullCheck = EarlyExitVulnerabilityCorrection.addNullCheck(factory, target);

        if (nullCheck != null) {
            return factory.createBinaryOperator(nullCheck, lengthCheck, BinaryOperatorKind.AND);
        }
        return lengthCheck;
    }
}