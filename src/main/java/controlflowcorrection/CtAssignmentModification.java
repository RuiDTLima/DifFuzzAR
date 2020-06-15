package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtVariableReference;
import spoon.support.reflect.code.CtAssignmentImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import java.util.List;

class CtAssignmentModification {
    private static final Logger logger = LoggerFactory.getLogger(CtAssignmentModification.class);

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables) {
        CtAssignment<?, ?> assignmentStatement = (CtAssignment<?, ?>) statement;
        CtExpression<?> assignment = assignmentStatement.getAssignment();
        boolean usesSecret = ControlFlowBasedVulnerabilityCorrection.usesSecret(assignment.toString(), secretVariables);
        logger.info("{} uses a secret? {}", assignment.toString(), usesSecret);
        if (usesSecret) {
            CtExpression<?> assigned = assignmentStatement.getAssigned();
            if (assigned instanceof CtArrayWrite) {
                CtArrayWrite<?> arrayWrite = (CtArrayWrite<?>) assigned;
                CtVariableReadImpl<?> localVariableReference = (CtVariableReadImpl<?>) arrayWrite.getTarget();
                CtVariableReference<?> variable = localVariableReference.getVariable();
                CtLocalVariable<?> localVariable = factory.createLocalVariable(variable.getType(), variable.getSimpleName(), null);
                secretVariables.add(localVariable);
            } else {
                CtVariable<?> variable = (CtVariable<?>) assigned;
                secretVariables.add(variable);
            }
        }
    }

    static CtStatement modifyAssignment(Factory factory, CtAssignmentImpl<?, ?> assignmentImpl) {
        logger.info("Found an assignment.");
        CtExpression<?> assigned = assignmentImpl.getAssigned();
        CtExpression<?> assignment = assignmentImpl.getAssignment();
        String newAssigned;
        String newAssignment = assignment.toString();
        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(assigned.toString())) {
            newAssigned = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(assigned.toString());
        } else {
            String type = assignmentImpl.getType().getSimpleName();
            int counter = ControlFlowBasedVulnerabilityCorrection.increaseCounter();
            newAssigned = ControlFlowBasedVulnerabilityCorrection.getNameForVariable() + counter;
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(assigned.toString(), newAssigned);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newAssigned, type);
        }
        if (assignment instanceof CtArrayRead) {
            CtArrayRead<?> newArrayRead = CtArrayModification.modifyArrayOperation(factory, (CtArrayRead<?>) assignment);
            newAssignment = newArrayRead.toString();
        } else if (assignment instanceof CtBinaryOperator){
            newAssignment = CtBinaryOperatorModification.modifyBinaryOperator(factory, (CtBinaryOperator<?>) assignment);
        }

        return factory.createCodeSnippetStatement(newAssigned + " = " + newAssignment);
    }
}
