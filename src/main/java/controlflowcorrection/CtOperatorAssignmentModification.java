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

    static CtStatement[] modifyOperatorAssignment(CtElement element, Factory factory,
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
        String newAssigned = "";

        if (usesDependableVariable(assignment, dependableVariables)) {
            dependableVariables.add(assignment.toString());
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