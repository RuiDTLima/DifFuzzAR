package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.*;
import util.NamingConvention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class CtLocalVariableModification {
	private static final Logger logger = LoggerFactory.getLogger(CtLocalVariableModification.class);

	/**
	 * This method is invoked when a local variable is found before finding an 'if' or cycle that uses a secret. If this
	 * variable is assigned any value that uses a secret variable then this variable is added to list of secret variables.
	 * @param statement	The local variable found.
	 * @param factory	The factory used to create new instructions. NOT USED.
	 * @param secretVariables	A list of the secret variables.
	 * @param publicArguments	The list of public arguments. NOT USED.
	 * @return	Returns an array of blocks where in the first index is the local variable and in the second index is null.
	 */
	static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory,
										  List<CtVariable<?>> secretVariables,
										  List<CtParameter<?>> publicArguments) {
		logger.info("Found a local variable while traversing the method.");

		CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
		CtExpression<?> assignment = localVariable.getAssignment();

		CtBlock<?>[] returnBlocks = new CtBlock[2];
		returnBlocks[0] = new CtBlockImpl<>();
		returnBlocks[1] = new CtBlockImpl<>();
		returnBlocks[0].addStatement(localVariable.clone());
		returnBlocks[1].addStatement(null);

		if (assignment == null) {
			logger.info("There is no assignment to the local variable.");
			return returnBlocks;
		}

		boolean usesSecret = ControlFlowBasedVulnerabilityCorrection.usesSecret(assignment.toString(), secretVariables);

		if (usesSecret) {
			logger.info("The variable {} was added to the list of secret variables.", localVariable.getSimpleName());
			secretVariables.add(localVariable);
		}
		return returnBlocks;
	}

	/**
	 * The method where a local variable is modified. If the value assigned uses a dependable variable then this variable
	 * is added to the set of dependable variables, otherwise a new variable is created to take its place.
	 * @param element	The local variable to be modified.
	 * @param factory	The factory used to create new instructions.
	 * @param initialStatement	The initial 'if' statement that started this modification. NOT USED.
	 * @param dependableVariables	A set containing the dependable variables.
	 * @param secretVariables	A list of secret variables. NOT USED.
	 * @return An array of statements where in the first index is the local variable received, and in the second index
	 * is the modified version of the local variable.
	 */
	static CtStatement[] modifyLocalVariable(CtElement element, Factory factory,
											 CtIfImpl initialStatement,
											 Set<String> dependableVariables,
											 List<CtVariable<?>> secretVariables) {
		logger.info("Found a local variable to modify");

		CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
		CtExpression<?> assignment = localVariable.getAssignment();
		boolean isDependableVariableInUse;

		if (assignment instanceof CtInvocation) {
			CtInvocation<?> invocation = (CtInvocation<?>) assignment;
			List<CtExpression<?>> invocationArguments = invocation.getArguments();
			List<CtExpression<?>> newArguments = new ArrayList<>(invocationArguments.size());
			boolean special = false;	//	TODO change name.
			for (CtExpression<?> argument : invocationArguments) {
				if (argument instanceof CtArrayRead) {
					CtArrayRead arrayRead = (CtArrayRead<?>) argument;
					CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();
					if (dependableVariables.contains(indexExpression.toString())) {
						special = true;
						CtArrayRead<?> newArrayRead = arrayRead.clone();
						newArrayRead.setIndexExpression(factory.createCodeSnippetExpression("0"));
						newArguments.add(newArrayRead);
					} else
						newArguments.add(argument);
				}
			}

			if (special) {
				CtInvocation<?> newInvocation = invocation.clone();
				newInvocation.setArguments(newArguments);

				CtLocalVariable newLocalVariable = localVariable.clone();
				newLocalVariable.setAssignment(newInvocation);

				return new CtStatement[]{localVariable, newLocalVariable};
			}

			isDependableVariableInUse = isDependableVariableInUse(dependableVariables, invocation);
		} else if (assignment instanceof CtVariableReadImpl) {
			isDependableVariableInUse = isDependableVariableInUse(dependableVariables, (CtVariableReadImpl<?>) assignment);
		} else if (assignment instanceof CtBinaryOperator) {
			isDependableVariableInUse = isDependableVariableInUse(dependableVariables, (CtBinaryOperator<?>) assignment);
		} else {
			isDependableVariableInUse = Arrays.stream(assignment.toString().split("\\."))
					.anyMatch(word -> dependableVariables.stream().anyMatch(secretVariable -> secretVariable.equals(word)));
		}

		CtLocalVariable<?> newLocalVariable = modifyStatement(factory, dependableVariables, localVariable, isDependableVariableInUse);
		return new CtStatement[]{localVariable, newLocalVariable};
	}

	/**
	 * The method to check if in this invocation a dependable variable is used. Either in the arguments or in the target.
	 * @param dependableVariables	A set containing the dependable variables.
	 * @param invocation	The invocation to check if it uses a dependable variable
	 * @return	True if it uses it uses a dependable variables, false otherwise.
	 */
	private static boolean isDependableVariableInUse(Set<String> dependableVariables, CtInvocation<?> invocation) {
		logger.info("Assignment is an invocation.");
		List<CtExpression<?>> expressionList = new ArrayList<>();

		List<CtExpression<?>> invocationArguments = invocation.getArguments();
		for (CtExpression<?> invocationArgument : invocationArguments) {
			if (invocationArgument instanceof CtArrayRead) {
				CtArrayRead<?> arrayRead = (CtArrayRead<?>) invocationArgument;
				CtExpression<?> target = arrayRead.getTarget();
				CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();

				expressionList.add(target);
				expressionList.add(indexExpression);
			} else if (invocationArgument instanceof CtVariableRead) {
				CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationArgument;
				expressionList.add(variableRead);
			} else if (invocationArgument instanceof CtInvocationImpl) {
				CtInvocationImpl<?> invocationImpl = (CtInvocationImpl<?>) invocationArgument;
				CtExpression<?> target = invocationImpl.getTarget();
				List<CtExpression<?>> arguments = invocationImpl.getArguments();

				expressionList.add(target);
				List<CtExpression<?>> collect = arguments.stream()
						.filter(argument -> !(argument instanceof CtLiteralImpl))
						.collect(Collectors.toList());
				expressionList.addAll(collect);
			}
		}

		expressionList.add(invocation.getTarget());

		return expressionList.stream()
				.anyMatch(ctExpression -> dependableVariables.stream()
						.anyMatch(dependableVariable -> dependableVariable.equals(ctExpression.toString())));
	}

	/**
	 * The method to check if in this variable read a dependable variable is used.
	 * @param dependableVariables	A set containing the dependable variables.
	 * @param variableRead	The variable read to be checked.
	 * @return	True if it uses it uses a dependable variables, false otherwise.
	 */
	private static boolean isDependableVariableInUse(Set<String> dependableVariables, CtVariableReadImpl<?> variableRead) {
		String variable = variableRead.getVariable().toString();
		return dependableVariables.stream().anyMatch(dependableVariable -> dependableVariable.equals(variable));
	}

	/**
	 * The method to check if in this binary operator a dependable variable is used.
	 * @param dependableVariables	A set containing the dependable variables.
	 * @param binaryOperator	The binary operator to be checked.
	 * @return	True if it uses it uses a dependable variables, false otherwise.
	 */
	private static boolean isDependableVariableInUse(Set<String> dependableVariables, CtBinaryOperator<?> binaryOperator) {
		boolean condition = false;
		CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
		CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();

		if (leftHandOperand instanceof CtBinaryOperator) {
			condition = isDependableVariableInUse(dependableVariables, (CtBinaryOperator<?>) leftHandOperand);
		} else if (leftHandOperand instanceof CtVariableReadImpl) {
			condition = isDependableVariableInUse(dependableVariables, (CtVariableReadImpl<?>) leftHandOperand);
		}
		if (rightHandOperand instanceof CtBinaryOperator) {
			condition = isDependableVariableInUse(dependableVariables, (CtBinaryOperator<?>) rightHandOperand);
		} else if (rightHandOperand instanceof CtVariableReadImpl) {
			condition = isDependableVariableInUse(dependableVariables, (CtVariableReadImpl<?>) rightHandOperand);
		}

		return condition;
	}

	/**
	 * The method where the statement is modified if a dependable variable isn't used, otherwise it will add the local
	 * variable to the set of dependable variables.
	 * @param factory	The factory used to create new instructions.
	 * @param dependableVariables	A set containing the dependable variables.
	 * @param localVariable	The local variable to modify.
	 * @param usesDependableVariable	The boolean to indicate if the local variable is assigned a value that uses a
	 *                                  dependable variable.
	 * @return	The new modified local variable or null if it is assigned a value that uses a dependable variable.
	 */
	private static CtLocalVariable<?> modifyStatement(Factory factory, Set<String> dependableVariables,
													  CtLocalVariable<?> localVariable,
													  boolean usesDependableVariable) {
		String variableName = localVariable.getSimpleName();
		if (usesDependableVariable) {
			dependableVariables.add(variableName);
			logger.info("A new variable was added to the dependable variables.");
			return null;
		} else {
			logger.info("A new local variable will be created.");
			String newVariable = NamingConvention.produceNewVariable();
			ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(variableName, newVariable);

			CtTypeReference variableType = localVariable.getType();
			CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
			CtLocalVariable<?> newLocalVariable = factory.createLocalVariable(variableType, newVariable, defaultExpression);
			Set<ModifierKind> variableModifiers = localVariable.getModifiers();
			newLocalVariable.setModifiers(variableModifiers);
			return newLocalVariable;
		}
	}
}