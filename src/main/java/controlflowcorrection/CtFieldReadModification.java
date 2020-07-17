package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtFieldReadImpl;
import java.util.Set;

public class CtFieldReadModification {
	private static final Logger logger = LoggerFactory.getLogger(CtFieldReadModification.class);

	/**
	 * A method where a field read is modified. Here if the target of the read exists in the list of dependable variables
	 * and its type is primitive then, the value to be assigned should the primitive type default values. The ones declared
	 * are the only ones found in development of the tool.
	 * @param factory   The factory used to create new expressions.
	 * @param expression    The field read expression received.
	 * @param dependableVariables   A set of the dependable variables
	 * @return  Returns either the operation of the field read or a default value of the primitive type.
	 */
	static CtExpression<?> modifyFieldRead(Factory factory, CtExpression<?> expression, Set<String> dependableVariables) {
		logger.info("Modifying a field read.");

		CtFieldReadImpl<?> fieldRead = (CtFieldReadImpl<?>) expression;
		CtExpression<?> returnedFieldRead = fieldRead;

		CtFieldReference<?> variable = fieldRead.getVariable();
		String targetName = fieldRead.getTarget().toString();
		CtTypeReference<?> declaringType = variable.getDeclaringType();

		if (dependableVariables.contains(targetName) && declaringType.isPrimitive()) {
			switch (declaringType.getSimpleName()) {
				case "String":
					returnedFieldRead = factory.createLiteral("");
					break;
				case "boolean":
					returnedFieldRead = factory.createLiteral(false);
					break;
				default :
					returnedFieldRead = factory.createLiteral(0);
					break;
			}
		}
		return returnedFieldRead;
	}
}