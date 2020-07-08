package controlflowcorrection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.support.reflect.code.*;
import util.NamingConvention;
import java.util.List;

class CtAssignmentModification {
    private static final Logger logger = LoggerFactory.getLogger(CtAssignmentModification.class);

    static CtBlock<?>[] traverseStatement(CtStatement statement, Factory factory, List<CtVariable<?>> secretVariables, List<CtParameter<?>> publicArguments) {
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

        CtBlock<?>[] returnBlocks = new CtBlock[2];
        returnBlocks[0] = new CtBlockImpl<>();
        returnBlocks[1] = new CtBlockImpl<>();
        returnBlocks[0].addStatement(assignmentStatement.clone());
        returnBlocks[1].addStatement(null);
        return returnBlocks;
    }

    static CtStatement[] modifyAssignment(CtElement element, Factory factory, CtIfImpl initialStatement, List<String> dependableVariables, List<CtVariable<?>> secretVariables) {
        logger.info("Found an assignment to modify.");

        CtAssignmentImpl<?, ?> assignmentImpl = (CtAssignmentImpl<?, ?>) element;
        CtAssignment<?, ?> oldAssignment = assignmentImpl.clone();
        CtTypeReference type = assignmentImpl.getType();
        CtExpression<?> assigned = assignmentImpl.getAssigned();
        CtExpression<?> assignment = assignmentImpl.getAssignment();
        String newAssigned;

        if (assignment instanceof CtArrayRead) {
            assignment = CtArrayModification.modifyArrayOperation(factory, (CtArrayRead<?>) assignment);
            logger.info("The assignment is an array read.");
        } else if (assignment instanceof CtBinaryOperator){
            assignment = CtBinaryOperatorModification.modifyBinaryOperator(factory, (CtBinaryOperator<Boolean>) assignment);
            logger.info("The assignment is of an binary operator.");
        } else if (assignment instanceof CtConditionalImpl) {
            CtConditionalImpl<?> conditional = (CtConditionalImpl<?>) assignment;
            String condition = conditional.getCondition().toString();
            String thenExpression = conditional.getThenExpression().toString();
            String elseExpression = conditional.getElseExpression().toString();
            if (dependableVariables.contains(condition) || dependableVariables.contains(thenExpression) || dependableVariables.contains(elseExpression)) {
                dependableVariables.add(assigned.toString());
                return new CtStatement[]{oldAssignment, null};
                //return null;
            } /*else if (dependableVariables.contains(thenExpression)) {
                dependableVariables.add(assigned.toString());
                return null;
            } else if (dependableVariables.contains(elseExpression)) {
                dependableVariables.add(assigned.toString());
                return null;
            }*/
        } else if (assignment instanceof CtFieldReadImpl) {
            CtFieldReadImpl<?> fieldRead = (CtFieldReadImpl<?>) assignment;
            CtFieldReference<?> variable = fieldRead.getVariable();
            String variableName = fieldRead.getTarget().toString();
            CtTypeReference<?> declaringType = variable.getDeclaringType();
            if (dependableVariables.contains(variableName)) {
                if (declaringType.isPrimitive()) {
                    if (declaringType.getSimpleName().equals("String")) {
                        assignment = factory.createLiteral("");
                    } else if (declaringType.getSimpleName().equals("boolean")) {
                        assignment = factory.createLiteral(false);
                    } else
                        assignment = factory.createLiteral(0);
                }
            }
        }

        if (ControlFlowBasedVulnerabilityCorrection.containsKeyVariablesReplacement(assigned.toString())) {
            newAssigned = ControlFlowBasedVulnerabilityCorrection.getValueVariablesReplacement(assigned.toString());
            logger.info("The assigned is a variable already replaced.");
        } else {
            newAssigned = NamingConvention.produceNewVariable();
            ControlFlowBasedVulnerabilityCorrection.addToVariablesReplacement(assigned.toString(), newAssigned);
            ControlFlowBasedVulnerabilityCorrection.addToVariablesToAdd(newAssigned, type, null);
            logger.info("The assigned is a variable to be replaced.");
        }

        CtLocalVariableReference variableReference = factory.createLocalVariableReference(type, newAssigned);
        CtAssignment<?, ?> variableAssignment = factory.createVariableAssignment(variableReference, false, assignment);
        variableAssignment.setType(type);
        return new CtStatement[]{oldAssignment, variableAssignment};
    }

    public static boolean equalAssignments(CtStatement firstStatement, CtStatement secondStatement) {
        CtAssignment<?, ?> firstAssignment = (CtAssignment<?, ?>) firstStatement;
        CtAssignment<?, ?> secondAssignment = (CtAssignment<?, ?>) secondStatement;

        return firstAssignment.getAssignment().equals(secondAssignment.getAssignment());
    }
}
