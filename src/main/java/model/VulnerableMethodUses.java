package model;

public class VulnerableMethodUses {
    private String firstUseCasePackageName;
    private String firstUseCaseClassName;
    private String firstUseCaseMethodName;
    private String[] firstUseCaseArgumentsNames;
    private String secondUseCasePackageName;
    private String secondUseCaseClassName;
    private String secondUseCaseMethodName;
    private String[] secondUseCaseArgumentsNames;

    public String getFirstUseCasePackageName() {
        return firstUseCasePackageName;
    }

    public String getFirstUseCaseClassName() {
        return firstUseCaseClassName;
    }

    public String getFirstUseCaseMethodName() {
        return firstUseCaseMethodName;
    }

    public String[] getFirstUseCaseArgumentsNames() {
        return firstUseCaseArgumentsNames;
    }

    public String getSecondUseCasePackageName() {
        return secondUseCasePackageName;
    }

    public String getSecondUseCaseClassName() {
        return secondUseCaseClassName;
    }

    public String getSecondUseCaseMethodName() {
        return secondUseCaseMethodName;
    }

    public String[] getSecondUseCaseArgumentsNames() {
        return secondUseCaseArgumentsNames;
    }

    public void setUseCase(String packageName, String className, String methodName, String[] arguments) {
        if (firstUseCaseMethodName == null) {
            firstUseCasePackageName = packageName;
            firstUseCaseClassName = className;
            firstUseCaseMethodName = methodName;
            firstUseCaseArgumentsNames = arguments;
        } else if (secondUseCaseMethodName == null) {
            secondUseCasePackageName = packageName;
            secondUseCaseClassName = className;
            secondUseCaseMethodName = methodName;
            secondUseCaseArgumentsNames = arguments;
        }
    }

    public void addFromOtherVulnerableMethodUses(VulnerableMethodUses vulnerableMethodUses) {
        if (this.firstUseCaseMethodName == null) {
            this.firstUseCasePackageName = vulnerableMethodUses.firstUseCasePackageName;
            this.firstUseCaseClassName = vulnerableMethodUses.firstUseCaseClassName;
            this.firstUseCaseMethodName = vulnerableMethodUses.firstUseCaseMethodName;
            this.firstUseCaseArgumentsNames = vulnerableMethodUses.firstUseCaseArgumentsNames;
        } else if (this.secondUseCaseMethodName == null) {
            this.secondUseCasePackageName = vulnerableMethodUses.firstUseCasePackageName;
            this.secondUseCaseClassName = vulnerableMethodUses.firstUseCaseClassName;
            this.secondUseCaseMethodName = vulnerableMethodUses.firstUseCaseMethodName;
            this.secondUseCaseArgumentsNames = vulnerableMethodUses.firstUseCaseArgumentsNames;
        }
    }

    public boolean isValid() {
        return firstUseCaseClassName != null
                && firstUseCaseClassName.equals(secondUseCaseClassName)
                && firstUseCaseMethodName.equals(secondUseCaseMethodName)
                && firstUseCaseArgumentsNames.length == secondUseCaseArgumentsNames.length;
    }
}
