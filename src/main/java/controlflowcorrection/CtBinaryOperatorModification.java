package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import java.util.Set;

class CtBinaryOperatorModification {
	private static final Logger logger = LoggerFactory.getLogger(CtBinaryOperatorModification.class);

	/**
	 * The method where a binary operator is modified. Here each hand of the binary operator will be modified depending on
	 * their type of operation, as it stands for the left hand it will be modified if the operand is a 'variable read' or
	 * an 'array read', while in the right hand it will be modified only if the operand is a 'variable read'.
	 * @param factory   The factory used to create new instructions
	 * @param expression    The expression representing the binary operator.
	 * @param dependableVariables   A set of the dependable variables.
	 * @return  Returns the binary operator received after the modifications implemented.
	 */
	static CtBinaryOperator<Boolean> modifyBinaryOperator(Factory factory, CtExpression<?> expression, Set<String> dependableVariables) {
		logger.info("Modifying a binary operator.");

		CtBinaryOperator<Boolean> binaryOperator = (CtBinaryOperator<Boolean>) expression;

		CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
		CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

		if (leftHandOperand instanceof CtVariableRead) {
			CtExpression<?> leftHandOperator = VariableReadModification.modifyVariableRead(factory, leftHandOperand);
			binaryOperator.setLeftHandOperand(leftHandOperator);
			logger.info("The left hand operand is a variable read.");
		} else if (leftHandOperand instanceof CtArrayRead) {
			CtArrayRead<?> leftOperand = CtArrayModification.modifyArrayOperation(factory, leftHandOperand, dependableVariables);
			binaryOperator.setLeftHandOperand(leftOperand);
			logger.info("The left hand operand is an array read.");
		}

		if (rightHandOperand instanceof CtVariableRead) {
			CtExpression<?> rightHandOperator = VariableReadModification.modifyVariableRead(factory, rightHandOperand);
			binaryOperator.setRightHandOperand(rightHandOperator);
			logger.info("The right hand operand is a variable read.");
		}

		return binaryOperator;
	}

	/**
	 * Checks if the two binary operators are equal. Meaning if they have the same kind (>, <, ==, !=, etc.), if the left
	 * hand of both operators are the equal and if the right hand of both operators are also equal.
	 * @param firstCondition    The first binary operator to be considered.
	 * @param secondCondition   The second binary operator to be considered.
	 * @return  True if the two binary operators are the same, False otherwise.
	 */
	static boolean equals(CtBinaryOperator<Boolean> firstCondition, CtBinaryOperator<Boolean> secondCondition) {
		BinaryOperatorKind firstConditionKind = firstCondition.getKind();
		BinaryOperatorKind secondConditionKind = secondCondition.getKind();

		CtExpression<?> firstConditionLeftHandOperand = firstCondition.getLeftHandOperand();
		CtExpression<?> firstConditionRightHandOperand = firstCondition.getRightHandOperand();

		CtExpression<?> secondConditionLeftHandOperand = secondCondition.getLeftHandOperand();
		CtExpression<?> secondConditionRightHandOperand = secondCondition.getRightHandOperand();

		boolean equals = firstConditionKind.equals(secondConditionKind);
		equals &= firstConditionLeftHandOperand.toString().equals(secondConditionLeftHandOperand.toString());
		equals &= firstConditionRightHandOperand.toString().equals(secondConditionRightHandOperand.toString());

		return equals;
	}
}