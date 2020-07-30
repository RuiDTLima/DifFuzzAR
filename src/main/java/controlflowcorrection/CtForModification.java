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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class CtForModification {
	private static final Logger logger = LoggerFactory.getLogger(CtForModification.class);

	/**
	 * This method is invoked when a 'for' statement is found. Here if the condition uses a secret variable it will be
	 * modified and the previous condition will be added to the 'for' body as an 'if' statement, while the existing body
	 * will be added to the 'then' block of the 'if'. Afterwards the modified body of the 'for' will be analysed in search
	 * of possible vulnerabilities.
	 * @param statement The statement representing the 'for'.
	 * @param factory   The factory used to create new instructions.
	 * @param secretVariables   A list of secret variables.
	 * @param publicArguments   The list of public arguments.
	 * @return  Returns an array of blocks where the first element contains the 'for' statement with the condition modified
	 * and the body after the addition of the 'if'. While in the second element will be a 'for' statement with the condition
	 * modified and the body after the addition of the 'if' also modified.
	 */
	static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables,
										  List<CtParameter<?>> publicArguments) {
		logger.info("Found a 'for' while traversing the method.");
		CtFor forStatement = (CtFor) statement;
		CtExpression<Boolean> forExpression = forStatement.getExpression();
		CtExpression<Boolean> oldCondition = forExpression.clone();
		CtBlock<?> body = (CtBlock<?>) forStatement.getBody();

		CtBlock<?>[] returnBlocks = new CtBlock[2];
		returnBlocks[0] = new CtBlockImpl<>();
		returnBlocks[1] = new CtBlockImpl<>();

		boolean modified = handleStoppingCondition(factory, secretVariables, publicArguments, forStatement, forExpression);
		if (modified && isIfNecessary(oldCondition, body)) {
			body = handleBody(oldCondition, body);
			forStatement.setBody(body);
		}
		CtBlock<?>[] returnedStatements = ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, body, secretVariables, publicArguments);

		CtBlock<?> oldBlock = returnedStatements[0];
		forStatement.setBody(oldBlock);
		returnBlocks[0].addStatement(forStatement.clone());

		CtBlock<?> newBlock = returnedStatements[1];
		newBlock.getStatements().forEach(element -> returnBlocks[1].addStatement(element.clone()));
		return returnBlocks;
	}

	private static boolean isIfNecessary(CtExpression<Boolean> oldCondition, CtBlock<?> body) {
		if (body.getStatements().size() == 1) {
			if (body.getStatement(0) instanceof CtIf) {
				CtIf existingIf = body.getStatement(0);
				CtExpression<Boolean> condition = existingIf.getCondition();
				return !condition.toString().contains(oldCondition.toString());
			}
		}
		return true;
	}

	/**
	 * The method where the body of the 'for' will be modified to be the 'then' block of an 'if' statement. While the condition
	 * is the previous condition of the 'for'.
	 * @param forExpression The previous condition of the 'for' and the new condition of the 'if'.
	 * @param body  The body of the for to be added to the 'then' block of the 'if'.
	 * @return  Returns a new block only with the 'if' block.
	 */
	private static CtBlock<?> handleBody(CtExpression<Boolean> forExpression, CtBlock<?> body) {
		CtIf newIf = new CtIfImpl();
		newIf.setCondition(forExpression);
		newIf.setThenStatement(body.clone());
		CtBlock<?> newBlock = new CtBlockImpl<>();
		newBlock.addStatement(newIf.clone());
		return newBlock;
	}

	/**
	 *  The method where the 'for' stopping condition is analysed to see if it uses a secret and thus needs to be updated.
	 *  As it stands only modifies if the stopping condition is a binary operator.
	 * @param factory   The factory used to create new instructions.
	 * @param secretVariables   A list of the secret variables.
	 * @param publicArguments   The list of public arguments.
	 * @param forStatement  The 'for' statement that might be altered.
	 * @param forExpression The stopping condition of the 'for' statement.
	 * @return  Returns 'true' if the stopping condition was modified and 'false' otherwise.
	 */
	private static boolean handleStoppingCondition(Factory factory, List<CtVariable<?>> secretVariables,
												   List<CtParameter<?>> publicArguments, CtFor forStatement,
												   CtExpression<Boolean> forExpression) {

		if (ControlFlowBasedVulnerabilityCorrection.usesSecret(forExpression.toString(), secretVariables)) {
			logger.info("Cycle stopping condition depends on the secret.");
			if (forExpression instanceof CtBinaryOperator) {
				CtBinaryOperator<Boolean> forCondition = (CtBinaryOperator<Boolean>) forExpression;
				CtBinaryOperator<Boolean> binaryOperator = modifyStoppingCondition(factory, secretVariables, publicArguments, forCondition.clone());
				if (!CtBinaryOperatorModification.equals(forCondition, binaryOperator)) {
					forStatement.setExpression(binaryOperator);
					logger.info("The for stopping condition is a binary operation that was modified.");
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A method to obtain all the conditions in the binary operator.
	 * @param condition Receives a condition to be analysed.
	 * @return returns a list containing all the binary operators found.
	 */
	private static List<CtBinaryOperator<?>> getConditions(CtExpression<?> condition) {
		List<CtBinaryOperator<?>> conditions = new ArrayList<>();
		if (condition instanceof CtBinaryOperator) {
			CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) condition;
			BinaryOperatorKind kind = binaryOperator.getKind();
			if (kind.equals(BinaryOperatorKind.AND) || kind.equals(BinaryOperatorKind.OR)) {
				CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
				CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();
				conditions.addAll(getConditions(leftHandOperand));
				conditions.addAll(getConditions(rightHandOperand));
			} else {
				conditions.add(binaryOperator);
			}
		}
		return conditions;
	}

	/**
	 * A method where the stopping condition of the 'for' will be altered. Here the will be attempted to replace a secret
	 * variable being used in the stopping condition by a public argument.
	 * @param factory   The factory used to create new instructions.
	 * @param secretVariables   A list of the secret variables.
	 * @param publicArguments   The list of public arguments.
	 * @param forExpression The stopping condition of the 'for' statement.
	 * @return  Returns the new stopping condition to be used in the 'for' statement.
	 */
	private static CtBinaryOperator<Boolean> modifyStoppingCondition(Factory factory, List<CtVariable<?>> secretVariables,
																	 List<CtParameter<?>> publicArguments,
																	 CtBinaryOperator<Boolean> forExpression) {

		logger.info("Modifying the stopping condition.");
		CtExpression<?> leftHandOperand = forExpression.getLeftHandOperand();
		CtExpression<?> rightHandOperand = forExpression.getRightHandOperand();
		String leftHandOperandString = leftHandOperand.toString();
		String rightHandOperandString = rightHandOperand.toString();
		CtBinaryOperator<Boolean> newStoppingCondition = factory.createBinaryOperator();
		newStoppingCondition.setKind(forExpression.getKind());

		for (CtVariable<?> secretArgument : secretVariables) {
			String secretArgumentSimpleName = secretArgument.getSimpleName();
			if (Arrays.stream(leftHandOperandString.split("\\."))
					.anyMatch(word -> word.matches(".*\\b" + secretArgumentSimpleName + "\\b.*"))) {

				leftHandOperand = modifyOperand(factory, secretArgument, publicArguments, leftHandOperand);
				logger.info("The left hand operand was modified.");
			} else if (Arrays.stream(rightHandOperandString.split("\\."))
					.anyMatch(word -> word.matches(".*\\b" + secretArgumentSimpleName + "\\b.*"))) {

				rightHandOperand = modifyOperand(factory, secretArgument, publicArguments, rightHandOperand);
				logger.info("The right hand operand was modified.");
			}
		}

		newStoppingCondition.setLeftHandOperand(leftHandOperand);
		newStoppingCondition.setRightHandOperand(rightHandOperand);
		return newStoppingCondition;
	}

	/**
	 * A method where the operand containing a secret variable will be modified to use instead a public argument of the
	 * same type.
	 * @param factory   The factory used to create new instructions.
	 * @param secretArgument A list of the secret variable.
	 * @param publicArguments   The list of public arguments.
	 * @param handOperand   The hand operand to be modified.
	 * @return  Returns the modified operand.
	 */
	private static CtExpression<?> modifyOperand(Factory factory, CtVariable<?> secretArgument,
												 List<CtParameter<?>> publicArguments, CtExpression<?> handOperand) {
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