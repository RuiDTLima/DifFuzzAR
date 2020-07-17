package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtIfImpl;
import java.util.List;
import java.util.Set;

class CtUnaryOperatorModification {
	private static final Logger logger = LoggerFactory.getLogger(CtUnaryOperatorModification.class);

	/**
	 * The method where a unary operator is modified. If the variable in the unary operator has been replaced by a newly
	 * created variable, then the unary operator will be modified to use that new variable.
	 * @param element	The unary operator to be modified.
	 * @param factory	The factory used to create new instructions.
	 * @param initialStatement	The initial 'if' statement that started this modification. NOT USED.
	 * @param dependableVariables	A set containing the dependable variables. NOT USED.
	 * @param secretVariables	A list of secret variables. NOT USED.
	 * @return	Returns an array of blocks where in the first index is the original unary operator and in the second index
	 * is the newly created unary operator.
	 */
	static CtBlock<?>[] modifyUnaryOperator(CtElement element, Factory factory, CtIfImpl initialStatement,
											Set<String> dependableVariables,
											List<CtVariable<?>> secretVariables) {
		logger.info("Found a unary operator to modify.");
		CtUnaryOperator<?> unaryOperator = (CtUnaryOperator<?>) element;
		CtStatement statement = unaryOperator.clone();
		String operand = unaryOperator.getOperand().toString();

		CtBlock<?> oldBlock = factory.createBlock().addStatement(unaryOperator.clone());

		if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(operand)) {
			String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(operand);
			CtExpression variableExpression = factory.createCodeSnippetExpression(valueVariablesReplacement);
			statement = unaryOperator.setOperand(variableExpression);
			logger.info("The unary operator was modified.");
		}
		CtBlock<?> newBlock = factory.createBlock().addStatement(statement.clone());

		return new CtBlock[]{oldBlock, newBlock};
	}
}