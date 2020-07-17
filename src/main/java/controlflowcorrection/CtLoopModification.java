package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtBlockImpl;
import java.util.List;

public class CtLoopModification {
	private static final Logger logger = LoggerFactory.getLogger(CtLoopModification.class);

	/**
	 * This method is invoked when a loop is found. The body of the loop will be analysed in search for a possible source
	 * of the vulnerability.
	 * @param statement	The loop to be analysed
	 * @param factory	The factory used to create new instructions.
	 * @param secretVariables	A list of the secret variables.
	 * @param publicArguments	The list containing the public arguments.
	 * @return	Returns an array of blocks where in the first index is the loop body with slight modifications and in the
	 * second index is a completely changed loop body.
	 */
	static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables,
										  List<CtParameter<?>> publicArguments) {
		logger.info("Found a loop while traversing the method.");
		CtLoop loopStatement = (CtLoop) statement;

		CtBlock<?>[] returnBlocks = new CtBlock[2];
		returnBlocks[0] = new CtBlockImpl<>();
		returnBlocks[1] = new CtBlockImpl<>();
		returnBlocks[0].addStatement(loopStatement.clone());

		CtBlock<?>[] returnedStatements = ControlFlowBasedVulnerabilityCorrection.traverseMethodBody(factory, ((CtBlock<?>) loopStatement.getBody()), secretVariables, publicArguments);

		CtBlock<?> oldBlock = returnedStatements[0];
		oldBlock.getStatements().forEach(element -> returnBlocks[0].addStatement(element.clone()));

		CtBlock<?> newBlock = returnedStatements[1];
		newBlock.getStatements().forEach(element -> returnBlocks[1].addStatement(element.clone()));
		return returnBlocks;
	}
}