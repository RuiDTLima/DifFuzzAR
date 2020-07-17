package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import java.util.Set;

class CtArrayModification {
	private static final Logger logger = LoggerFactory.getLogger(CtArrayModification.class);

	/**
	 * The modification of an array read operation. Here the target, meaning the name of the array or the index might be swapped
	 * by a previously created variable.
	 * @param factory   The factory used to create new instructions.
	 * @param expression    The array read expression.
	 * @param dependableVariables   A set of the dependable variables. It has no use here, but is passed so that the function
	 *                              pattern can be employed.
	 * @return  Returns the new array read operation.
	 */
	static CtArrayRead<?> modifyArrayOperation(Factory factory, CtExpression<?> expression, Set<String> dependableVariables) {
		logger.info("Modifying an array operation.");

		CtArrayRead<?> arrayRead = (CtArrayRead<?>) expression;

		CtExpression<?> target = arrayRead.getTarget();
		CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();
		CtArrayRead<?> newArrayRead = arrayRead.clone();

		if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(target.toString())) {
			String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(target.toString());
			CtCodeSnippetExpression<?> newTarget = factory.createCodeSnippetExpression(valueVariablesReplacement);
			newArrayRead.setTarget(newTarget);
			logger.info("Changed the target of the array read.");
		}

		if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(indexExpression.toString())) {
			String valueVariablesReplacement = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(indexExpression.toString());
			CtCodeSnippetExpression<Integer> newIndex = factory.createCodeSnippetExpression(valueVariablesReplacement);
			newArrayRead.setIndexExpression(newIndex);
			logger.info("Changed the index of the array read.");
		}

		return newArrayRead;
	}
}