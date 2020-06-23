package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class CtForModification {
    private static final Logger logger = LoggerFactory.getLogger(CtForModification.class);

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found a 'for' while traversing the method.");
        CtFor forStatement = (CtFor) statement;
        CtExpression<Boolean> forExpression = forStatement.getExpression();
        CtBlock<?> body = (CtBlock<?>) forStatement.getBody();
        CtBlock<?> newBody = handleBody(forExpression, body); // TODO check if it can be after modifying the condition.
        forStatement.setBody(newBody);
        handleStoppingCondition(factory, secretVariables, publicArguments, forStatement, forExpression);
        ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, newBody, secretVariables, publicArguments);
    }

    private static CtBlock<?> handleBody(CtExpression<Boolean> forExpression, CtBlock<?> body) {
        CtIf newIf = new CtIfImpl();
        newIf.setCondition(forExpression);
        newIf.setThenStatement(body.clone());
        CtBlock<?> newBlock = new CtBlockImpl<>();
        newBlock.addStatement(newIf.clone());
        return newBlock;
    }

    private static void handleStoppingCondition(Factory factory, List<CtVariable<?>> secretVariables,
                                                List<CtParameter<?>> publicArguments, CtFor forStatement,
                                                CtExpression<Boolean> forExpression) {

        if (ControlFlowBasedVulnerabilityCorrection.usesSecret(forExpression.toString(), secretVariables)) {
            logger.info("Cycle stopping condition depends on the secret.");
            if (forExpression instanceof CtBinaryOperator) {
                CtBinaryOperator<Boolean> binaryOperator = modifyStoppingCondition(factory, secretVariables, publicArguments, (CtBinaryOperator<Boolean>) forExpression);
                forStatement.setExpression(binaryOperator);
                logger.info("The for stopping condition is a binary operation that was modified.");
            }
        }
    }

    private static CtBinaryOperator<Boolean> modifyStoppingCondition(Factory factory, List<CtVariable<?>> secretVariables,
                                                                     List<CtParameter<?>> publicArguments,
                                                                     CtBinaryOperator<Boolean> forExpression) {

        logger.info("Modifying the stopping condition.");
        CtExpression<?> leftHandOperand = forExpression.getLeftHandOperand();
        CtExpression<?> rightHandOperand = forExpression.getRightHandOperand();
        String leftHandOperandString = leftHandOperand.toString();
        String rightHandOperandString = rightHandOperand.toString();

        for (CtVariable<?> secretArgument : secretVariables) {
            String secretArgumentSimpleName = secretArgument.getSimpleName();
            if (Arrays.stream(leftHandOperandString.split("\\."))
                    .anyMatch(word -> word.matches(".*\\b" + secretArgumentSimpleName + "\\b.*"))) {

                leftHandOperand = modifyOperand(factory, publicArguments, leftHandOperand, secretArgument);
                logger.info("The left hand operand was modified.");
            } else if (Arrays.stream(rightHandOperandString.split("\\."))
                    .anyMatch(word -> word.matches(".*\\b" + secretArgumentSimpleName + "\\b.*"))) {

                rightHandOperand = modifyOperand(factory, publicArguments, rightHandOperand, secretArgument);
                logger.info("The right hand operand was modified.");
            }
        }

        forExpression.setLeftHandOperand(leftHandOperand);
        forExpression.setRightHandOperand(rightHandOperand);
        return forExpression;
    }

    private static CtExpression<?> modifyOperand(Factory factory, List<CtParameter<?>> publicArguments, CtExpression<?> handOperand, CtVariable<?> secretArgument) {
        logger.info("Modifying the operand");
        String handOperandString = handOperand.toString();
        String secretArgumentSimpleName = secretArgument.getSimpleName();
        CtTypeReference<?> secretArgumentType = secretArgument.getType();

        Optional<CtParameter<?>> optionalPublicArgument = publicArguments.stream()
                .filter(publicArgument -> publicArgument.getType().equals(secretArgumentType))
                .findFirst();

        String newHandOperand;
        if (optionalPublicArgument.isPresent()) {
            String publicArgumentSimpleName = optionalPublicArgument.get().getSimpleName();
            newHandOperand = handOperandString.replace(secretArgumentSimpleName, publicArgumentSimpleName);
            handOperand = factory.createCodeSnippetExpression(newHandOperand);
            logger.info("The hand operand was replaced by a public argument.");
        }
        return handOperand;
    }
}
