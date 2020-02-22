package utils;

public class VulnerableMethodUses {
    private String firstUseCaseMethodName;
    private String[] firstUseCaseArgumentsNames;
    private String secondUseCaseMethodName;
    private String[] secondUseCaseArgumentsNames;

    public String getFirstUseCaseMethodName() {
        return firstUseCaseMethodName;
    }

    public String[] getFirstUseCaseArgumentsNames() {
        return firstUseCaseArgumentsNames;
    }

    public String getSecondUseCaseMethodName() {
        return secondUseCaseMethodName;
    }

    public String[] getSecondUseCaseArgumentsNames() {
        return secondUseCaseArgumentsNames;
    }

    public void setUseCase(String useCase) {
        if (firstUseCaseMethodName == null) {
            firstUseCaseMethodName = useCase.substring(useCase.indexOf("= ") + 1, useCase.indexOf("(")).replace(" ", "");
            firstUseCaseArgumentsNames = useCase.substring(useCase.indexOf("(") + 1, useCase.indexOf(")")).split(",");
        } else if (secondUseCaseMethodName == null) {
            secondUseCaseMethodName = useCase.substring(useCase.indexOf("= ") + 1, useCase.indexOf("(")).replace(" ", "");
            secondUseCaseArgumentsNames = useCase.substring(useCase.indexOf("(") + 1, useCase.indexOf(")")).split(",");
        }
    }

    public void addFromOtherVulnerableMethodUses(VulnerableMethodUses vulnerableMethodUses) {
        if (this.firstUseCaseMethodName == null) {
            this.firstUseCaseMethodName = vulnerableMethodUses.firstUseCaseMethodName;
            this.firstUseCaseArgumentsNames = vulnerableMethodUses.firstUseCaseArgumentsNames;
        } else if (this.secondUseCaseMethodName == null) {
            this.secondUseCaseMethodName = vulnerableMethodUses.firstUseCaseMethodName;
            this.secondUseCaseArgumentsNames = vulnerableMethodUses.firstUseCaseArgumentsNames;
        }
    }

    public boolean isValid() {
        return firstUseCaseMethodName != null
                && firstUseCaseMethodName.equals(secondUseCaseMethodName)
                && firstUseCaseArgumentsNames.length == secondUseCaseArgumentsNames.length;
    }
}
