package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.support.reflect.code.CtAssignmentImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import util.NamingConvention;
import java.util.List;

class CtAssignmentModification {
    private static final Logger logger = LoggerFactory.getLogger(CtAssignmentModification.class);

    static void traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
        logger.info("Found an assignment while traversing the method.");

        CtAssignment<?, ?> assignmentStatement = (CtAssignment<?, ?>) statement;
        CtExpression<?> assignment = assignmentStatement.getAssignment();
        boolean usesSecret = ControlFlowBasedVulnerabilityCorrection.usesSecret(assignment.toString(), secretVariables);

        logger.info("{} Does assignment uses a secret? {}", assignment.toString(), usesSecret);

        if (usesSecret) {
            CtExpression<?> assigned = assignmentStatement.getAssigned();

            if (assigned instanceof CtArrayWrite) {
                CtArrayWrite<?> arrayWrite = (CtArrayWrite<?>) assigned;
                CtVariableReadImpl<?> localVariableReference = (CtVariableReadImpl<?>) arrayWrite.getTarget();
                CtVariableReference<?> variable = localVariableReference.getVariable();
                CtLocalVariable<?> localVariable = factory.createLocalVariable(variable.getType(), variable.getSimpleName(), null);
                secretVariables.add(localVariable);
                logger.info("The assignment is an array write where the target is now a secret variable");
            } else if (assigned instanceof CtVariableWrite) {
                CtVariableWrite<?> variable = (CtVariableWrite<?>) assigned;
                secretVariables.add(variable.getVariable().getDeclaration());
            } else{
                CtVariable<?> variable = (CtVariable<?>) assigned;
                secretVariables.add(variable);
                logger.info("The assignment is to a variable, that is now a secret variable.");
            }
        }
    }

    static CtStatement modifyAssignment(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found an assignment to modify.");

        CtAssignmentImpl<?, ?> assignmentImpl = (CtAssignmentImpl<?, ?>) element;
        CtTypeReference<?> type = assignmentImpl.getType();
        CtExpression<?> assigned = assignmentImpl.getAssigned();
        CtExpression<?> assignment = assignmentImpl.getAssignment();
        String newAssigned;

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(assigned.toString())) {
            newAssigned = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(assigned.toString());
            logger.info("The assigned is a variable already replaced.");
        } else {
            newAssigned = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(assigned.toString(), newAssigned);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newAssigned, type);
            logger.info("The assigned is a variable to be replaced.");
        }

        if (assignment instanceof CtArrayRead) {
            assignment = CtArrayModification.modifyArrayOperation(factory, (CtArrayRead<?>) assignment);
            logger.info("The assignment is an array read.");
        } else if (assignment instanceof CtBinaryOperator){
            assignment = CtBinaryOperatorModification.modifyBinaryOperator(factory, (CtBinaryOperator<Boolean>) assignment);
            logger.info("The assignment is of an binary operator.");
        }

        CtLocalVariableReference variableReference = factory.createLocalVariableReference(type, newAssigned);
        return factory.createVariableAssignment(variableReference, false, assignment);
    }

    public static boolean equalAssignments(CtStatement firstStatement, CtStatement secondStatement) {
        CtAssignment<?, ?> firstAssignment = (CtAssignment<?, ?>) firstStatement;
        CtAssignment<?, ?> secondAssignment = (CtAssignment<?, ?>) secondStatement;

        return firstAssignment.getAssignment().equals(secondAssignment.getAssignment());
    }
}
