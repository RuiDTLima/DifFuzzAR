import model.VulnerableMethodUses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DifFuzzAR {
    private static final Logger logger = LoggerFactory.getLogger(DifFuzzAR.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.warn("There needs to be at least one argument for the application.");
            return;
        }
        String driverPath = args[0];
        logger.info("The passed path was {}.", driverPath);

        Optional<VulnerableMethodUses> optionalVulnerableMethodUsesCases = FindVulnerableMethod.processDriver(driverPath);

        if (!optionalVulnerableMethodUsesCases.isPresent()) {
            logger.warn("DifFuzzAR could not find the vulnerable method indicated in the Driver {}.", driverPath);
            return;
        }

        VulnerableMethodUses vulnerableMethodUses = optionalVulnerableMethodUsesCases.get();

        ModificationOfCode.processVulnerableClass(driverPath, vulnerableMethodUses);
        logger.info("Finish the automatic repair.\n BE ADVISED: The corrected code may be logical flawed. Use the modified code as a template.");
    }
}