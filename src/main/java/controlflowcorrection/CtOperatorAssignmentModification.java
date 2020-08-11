package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtOperatorAssignmentImpl;
import util.NamingConvention;
import java.util.List;
import java.util.Set;

class CtOperatorAssignmentModification {
    private static final Logger logger = LoggerFactory.getLogger(CtOperatorAssignmentModification.class);

    /**
     * The method where a operator assignment is modified. If the value being assigned uses a dependable variable, then
     * the variable assigned to is added to the set of dependable variables.
     * @param element               The operator assignment to be modified.
     * @param factory               The factory used to create new instructions.
     * @param initialStatement      The initial 'if' statement that started this modification. NOT USED.
     * @param dependableVariables   A set containing the dependable variables.
     * @param secretVariables       A list of secret variables. NOT USED.
     * @return                      An array of statements where in the first index is the operator assignment received,
     *                              and in the second index is the modified version of the operator assignment.
     */
    static CtStatement[] modifyOperatorAssignment(CtElement element,
                                                  Factory factory,
                                                  CtIfImpl initialStatement,
                                                  Set<String> dependableVariables,
                                                  List<CtVariable<?>> secretVariables) {
        logger.info("Found an operator assignment.");
        CtOperatorAssignment<?, ?> operatorAssignment = (CtOperatorAssignmentImpl<?, ?>) element;
        CtOperatorAssignment<?, ?> newOperatorAssignment = operatorAssignment.clone();
        String originalVariableName = operatorAssignment.getAssigned().toString();
        CtExpression<?> assignment = operatorAssignment.getAssignment();
        CtTypeReference<?> type = operatorAssignment.getType();
        CtExpression newAssignment = assignment.clone();
        String newAssigned;

        if (usesDependableVariable(assignment, dependableVariables)) {
            dependableVariables.add(originalVariableName);
            if (type.isPrimitive()) {
                switch (type.getSimpleName()) {
                    case "String":
                        newAssignment = factory.createLiteral("");
                        break;
                    case "boolean":
                        newAssignment = factory.createLiteral(false);
                        break;
                    default:
                        newAssignment = factory.createLiteral(0);
                        break;
                }
            }
        }

        newOperatorAssignment.setAssignment(newAssignment);

        if (ControlFlowBasedVulnerabilityCorrection.isKeyInVariablesReplacement(originalVariableName)) {
            newAssigned = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(originalVariableName);
        } else {
            newAssigned = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(originalVariableName, newAssigned);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newAssigned, type, null);
        }

        newOperatorAssignment.setAssigned(factory.createCodeSnippetExpression(newAssigned));
        return new CtStatement[]{operatorAssignment, newOperatorAssignment};
    }

    /**
     * The method used to check if the 'assignment' uses a dependable variable.
     * @param assignment            The assignment to check.
     * @param dependableVariables   The set of dependable variables.
     * @return                      Returns true if any variable used in 'assignment' is a dependable variable.
     *                              False otherwise.
     */
    private static boolean usesDependableVariable(CtExpression<?> assignment, Set<String> dependableVariables) {
        if (assignment instanceof CtBinaryOperator) {
            CtBinaryOperator<?> binaryOperator = (CtBinaryOperator<?>) assignment;
            CtExpression<?> leftHandOperand = binaryOperator.getLeftHandOperand();
            CtExpression<?> rightHandOperand = binaryOperator.getRightHandOperand();
            return usesDependableVariable(leftHandOperand, dependableVariables)
                    || usesDependableVariable(rightHandOperand, dependableVariables);

        } else if (assignment instanceof CtArrayRead) {
            CtArrayRead<?> arrayRead = (CtArrayRead<?>) assignment;
            CtExpression<?> target = arrayRead.getTarget();
            CtExpression<Integer> indexExpression = arrayRead.getIndexExpression();
            return dependableVariables.contains(target.toString())
                    || dependableVariables.contains(indexExpression.toString());
        }
        return false;
    }
}