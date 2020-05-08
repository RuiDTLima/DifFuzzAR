import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.ReturnOrThrowFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;

import java.util.*;

public class ModificationOfCode {
    private static final Logger logger = LoggerFactory.getLogger(ModificationOfCode.class);
    private static final String CLASS_NAME_ADDITION = "$Modification";

    public static void processVulnerableClass(String driverPath, VulnerableMethodUses vulnerableMethodUsesCases) {
        String packageName = vulnerableMethodUsesCases.getFirstUseCasePackageName();
        String className = vulnerableMethodUsesCases.getFirstUseCaseClassName();
        String methodName = vulnerableMethodUsesCases.getFirstUseCaseMethodName();

        logger.info("The vulnerable method {} belongs to the class {}.", methodName, className);

        String pathToCorrectedClass = driverPath.substring(0, driverPath.lastIndexOf("\\")) + "\\"; // The new class will add the necessary package
        String pathToVulnerableMethod = pathToCorrectedClass + packageName + "\\" + className + ".java";

        logger.info("The path to the vulnerable class is {}.", pathToVulnerableMethod);

        Launcher launcher = Setup.setupLauncher(pathToVulnerableMethod, pathToCorrectedClass);

        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();

        List<CtClass<?>> classList = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
        CtMethod<?> vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName)).first();

        CtClass<?> vulnerableClass;
        //if (classList.size() == 2) {
            vulnerableClass = classList.get(0);

            if (vulnerableMethod == null) {
                CtTypeReference<?> superClass = vulnerableClass.getSuperclass();
                String superclass = superClass.getQualifiedName().replace(".", "\\");
                className = superClass.getSimpleName();
                pathToVulnerableMethod = pathToCorrectedClass + superclass + ".java";
                launcher.addInputResource(pathToVulnerableMethod);
                launcher = Setup.setupLauncher(pathToVulnerableMethod, pathToCorrectedClass);
                model = launcher.buildModel();
                factory = launcher.getFactory();
                vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName)).first();
                classList = model.filterChildren(new TypeFilter<>(CtClass.class)).list();
                if (classList.size() == 1)
                    vulnerableClass = classList.get(0);
            }
            vulnerableClass.setSimpleName(className + CLASS_NAME_ADDITION);

            modifyCode(factory, vulnerableMethod, model, vulnerableMethodUsesCases);
            launcher.prettyprint();
       /* } else
            logger.warn("The inspected file contains {} classes, can't detect the vulnerable one.", classList.size());*/
    }

    /**
     * Where the process of code modification takes place. Retrieves all the return statements of the vulnerableMethod and
     * modifies them one by one in order of appearance.
     * @param factory   The factory used to create code snippets to add.
     * @param vulnerableMethod  The method with the vulnerability that will be modified.
     * @param model The model of the code. Represents the file with the code to be modified.
     * @param vulnerableMethodUsesCases
     */
    private static void modifyCode(Factory factory, CtMethod<?> vulnerableMethod, CtModel model, VulnerableMethodUses vulnerableMethodUsesCases) {
        List<CtStatement> statementList = vulnerableMethod.getBody().getStatements();

        if (statementList.size() == 1 && !(statementList.get(0) instanceof CtIfImpl)) {
            String methodInvocation = ((CtReturnImpl<?>) statementList.get(0)).getReturnedExpression().prettyprint();
            String calledMethodName = methodInvocation.substring(0, methodInvocation.indexOf("("));
            vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(calledMethodName)).first();
            modifyCode(factory, vulnerableMethod, model, vulnerableMethodUsesCases);
            return;
        }

        CtMethod<?> modifiedMethod = vulnerableMethod.copyMethod();
        Refactoring.changeMethodName(modifiedMethod, vulnerableMethod.getSimpleName() + CLASS_NAME_ADDITION);

        List<CtCFlowBreak> returnList = modifiedMethod.getElements(new ReturnOrThrowFilter());
        returnList.removeIf(returnOrThrow -> !(returnOrThrow instanceof CtReturnImpl));

        if (returnList.size() > 1) {
            logger.info("The method suffers from early-exit timing side-channel vulnerability since it " +
                    "has {} exit points.", returnList.size());
            EarlyExitVulnerabilityCorrection.correctVulnerability(factory, modifiedMethod, returnList);
        } else {
            logger.info("The method suffers from control-flow-based timing side-channel vulnerability.");
            ControlFlowBasedVulnerabilityCorrection.correctVulnerability(factory, modifiedMethod, vulnerableMethodUsesCases);
        }
    }
}
