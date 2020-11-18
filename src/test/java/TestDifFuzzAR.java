import model.VulnerableMethodUses;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import spoon.Launcher;
import spoon.legacy.NameFilter;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import util.Setup;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import static org.testng.Assert.fail;

public class TestDifFuzzAR {
    //region DataProviders
    @DataProvider
    /*
        String classpath,
        String expectedClassName,
        String expectedMethodName
     */
    private Object[][] findVulnerableMethodAndClass() {
        return new Object[][] {
                {
                    "apache_ftpserver_clear_safe/Driver_Clear.java",
                    "ClearTextPasswordEncryptor",
                    "matches"
                },
                {
                    "apache_ftpserver_clear_unsafe/Driver_Clear.java",
                    "ClearTextPasswordEncryptor",
                    "isEqual_unsafe"
                },
                {
                    "apache_ftpserver_md5_safe/Driver_MD5.java",
                    "Md5PasswordEncryptor",
                    "matches"
                },
                {
                    "apache_ftpserver_md5_unsafe/Driver_MD5.java",
                    "Md5PasswordEncryptor",
                    "matches"
                },
                {
                    "apache_ftpserver_salted_encrypt_unsafe/Driver_Salted_Encrypt.java",
                    "SaltedPasswordEncryptor",
                    "encrypt"
                },
                {
                    "apache_ftpserver_salted_safe/Driver_Salted.java",
                    "SaltedPasswordEncryptor",
                    "matches"
                },
                {
                    "apache_ftpserver_salted_unsafe/Driver_Salted.java",
                    "SaltedPasswordEncryptor",
                    "matches"
                },
                {
                    "apache_ftpserver_stringutils_safe/Driver_StringUtilsPad.java",
                    "StringUtils",
                    "pad_unsafe"
                },
                {
                    "apache_ftpserver_stringutils_unsafe/Driver_StringUtilsPad.java",
                    "StringUtils",
                    "pad_unsafe"
                },
                {
                    "blazer_array_safe/MoreSanity_Array_FuzzDriver.java",
                    "MoreSanity",
                    "array_safe"
                },
                {
                    "blazer_array_unsafe/MoreSanity_Array_FuzzDriver.java",
                    "MoreSanity",
                    "array_unsafe"
                },
                {
                    "blazer_gpt14_safe/GPT14_FuzzDriver.java",
                    "GPT14",
                    "modular_exponentiation_safe"
                },
                {
                    "blazer_gpt14_unsafe/GPT14_FuzzDriver.java",
                    "GPT14",
                    "modular_exponentiation_inline_unsafe"
                },
                {
                    "blazer_k96_safe/K96_FuzzDriver.java",
                    "K96",
                    "modular_exponentiation_safe"
                },
                {
                    "blazer_k96_unsafe/K96_FuzzDriver.java",
                    "K96",
                    "modular_exponentiation_unsafe"
                },
                {
                    "blazer_loopandbranch_safe/MoreSanity_LoopAndBranch_FuzzDriver.java",
                    "MoreSanity",
                    "loopAndbranch_safe"
                },
                {
                    "blazer_loopandbranch_unsafe/MoreSanity_LoopAndBranch_FuzzDriver.java",
                    "MoreSanity",
                    "loopAndbranch_unsafe"
                },
                {
                    "blazer_modpow1_safe/ModPow1_FuzzDriver.java",
                    "ModPow1",
                    "modPow1_safe"
                },
                {
                    "blazer_modpow1_unsafe/ModPow1_FuzzDriver.java",
                    "ModPow1",
                    "modPow1_unsafe"
                },
                {
                    "blazer_modpow2_safe/ModPow2_FuzzDriver.java",
                    "ModPow2",
                    "modPow2_safe"
                },
                {
                    "blazer_modpow2_unsafe/ModPow2_FuzzDriver.java",
                    "ModPow2",
                    "modPow2_unsafe"
                },
                {
                    "blazer_passwordEq_safe/User_FuzzDriver.java",
                    "User",
                    "passwordsEqual_safe"
                },
                {
                    "blazer_passwordEq_unsafe/User_FuzzDriver.java",
                    "User",
                    "passwordsEqual_unsafe"
                },
                {
                    "blazer_sanity_safe/Sanity_FuzzDriver.java",
                    "Sanity",
                    "sanity_safe"
                },
                {
                    "blazer_sanity_unsafe/Sanity_FuzzDriver.java",
                    "Sanity",
                    "sanity_unsafe"
                },
                {
                    "blazer_straightline_safe/Sanity_FuzzDriver.java",
                    "Sanity",
                    "straightline_safe"
                },
                {
                    "blazer_straightline_unsafe/Sanity_FuzzDriver.java",
                    "Sanity",
                    "straightline_unsafe"
                },
                {
                    "blazer_unixlogin_safe2/Timing_FuzzDriver.java",
                    "Timing",
                    "login_safe"
                },
                {
                    "blazer_unixlogin_unsafe2/Timing_FuzzDriver.java",
                    "Timing",
                    "login_unsafe"
                },
                {
                    "example_PWCheck_safe/Driver.java",
                    "PWCheck",
                    "pwcheck3_safe"
                },
                {
                    "example_PWCheck_unsafe/Driver.java",
                    "PWCheck",
                    "pwcheck1_unsafe"
                },
                {
                    "github_authmreloaded_safe/Driver.java",
                    "RoyalAuth",
                    "isEqual_unsafe"
                },
                {
                    "github_authmreloaded_unsafe/Driver.java",
                    "RoyalAuth",
                    "isEqual_unsafe"
                },
                {
                    "stac_crime_unsafe/CRIME_Driver.java",
                    "LZ77T",
                    "compress"
                },
                {
                    "stac_ibasys_unsafe/ImageMatcher_FuzzDriver.java",
                    "ImageMatcherWorker",
                    "test"
                },
                {
                    "themis_boot-stateless-auth_safe/Driver.java",
                    "TokenHandler",
                    "parseUserFromToken"
                },
                {
                    "themis_boot-stateless-auth_unsafe/Driver.java",
                    "TokenHandler",
                    "parseUserFromToken_unsafe"
                },
                {
                    "themis_dynatable_unsafe2/Driver.java",
                    "SchoolCalendarServiceImpl",
                    "getPeople"
                },
                {
                    "themis_GWT_advanced_table_unsafe2/Driver.java",
                    "UsersTableModelServiceImpl",
                    "getRows"
                },
                {
                    "themis_jdk_safe/MessageDigest_FuzzDriver.java",
                    "MessageDigest",
                    "isEqual_safe"
                },
                {
                    "themis_jdk_unsafe/MessageDigest_FuzzDriver.java",
                    "MessageDigest",
                    "isEqual_unsafe"
                },
                {
                    "themis_jetty_safe/Credential_FuzzDriver.java",
                    "Credential",
                    "stringEquals_safe"
                },
                {
                    "themis_jetty_unsafe/Credential_FuzzDriver.java",
                    "Credential",
                    "stringEquals_original"
                },
                {
                    "themis_oacc_unsafe/Driver.java",
                    "PasswordCredentials",
                    "equals"
                },
                {
                    "themis_oacc_unsafe2/Driver.java",
                    "PasswordCredentials",
                    "ArraysIsEquals"
                },
                {
                    "themis_openmrs-core_unsafe/Driver.java",
                    "Security",
                    "hashMatches"
                },
                {
                    "themis_orientdb_safe/OSecurityManager_FuzzDriver.java",
                    "OSecurityManager",
                    "checkPassword_safe"
                },
                {
                    "themis_orientdb_unsafe/OSecurityManager_FuzzDriver.java",
                    "OSecurityManager",
                    "equals_inline"
                },
                {
                    "themis_pac4j_safe2/Driver.java",
                    "DbAuthenticator",
                    "validate_safe"
                },
                {
                    "themis_pac4j_unsafe2/Driver.java",
                    "DbAuthenticator",
                    "validate_unsafe"
                },
                {
                    "themis_pac4j_unsafe_ext/Driver.java",
                    "DbAuthenticator",
                    "validate_unsafe"
                },
                {
                    "themis_picketbox_safe/UsernamePasswordLoginModule_FuzzDriver.java",
                    "UsernamePasswordLoginModule",
                    "validatePassword_safe"
                },
                {
                    "themis_picketbox_unsafe/UsernamePasswordLoginModule_FuzzDriver.java",
                    "UsernamePasswordLoginModule",
                    "equals"
                },
                {
                    "themis_spring-security_safe/PasswordEncoderUtils_FuzzDriver.java",
                    "PasswordEncoderUtils",
                    "equals_safe"
                },
                {
                    "themis_spring-security_unsafe/PasswordEncoderUtils_FuzzDriver.java",
                    "PasswordEncoderUtils",
                    "equals_unsafe"
                },
                {
                    "themis_tomcat_safe/Tomcat_FuzzDriver.java",
                    "DataSourceRealm",
                    "authenticate_safe"
                },
                {
                    "themis_tomcat_unsafe/Tomcat_FuzzDriver.java",
                    "DataSourceRealm",
                    "authenticate_unsafe"
                },
                {
                    "themis_tourplanner_safe/Driver.java",
                    "TourServlet",
                    "doGet"
                },
                {
                    "themis_tourplanner_unsafe/Driver.java",
                    "TourServlet",
                    "doGet"
                }
        };
    }

    @DataProvider
    /*
        String classpath
     */
    private Object[][] invalidDrivers() {
        return new Object[][] {
                {"blazer_unixlogin_unsafe/Timing_FuzzDriver.java"},
                {"blazer_unixlogin_safe/Timing_FuzzDriver.java"},
                {"themis_dynatable_unsafe/Driver.java"},
                {"themis_GWT_advanced_table_unsafe/Driver.java"},
                {"themis_pac4j_safe/Driver.java"},
                {"themis_pac4j_unsafe/Driver.java"}
        };
    }

    @DataProvider
    /*
      String pathToVulnerableMethod,
      String correctedClassName,
      String methodName,
      String correctedMethodPath,
      String[] firstUseCaseArgumentsNames,
      String[] secondUseCaseArgumentsNames
     */
    private Object[][] correctVulnerableMethodWithEarlyExit() {
        return new Object[][] {
                {
                    "apache_ftpserver_clear_unsafe/ClearTextPasswordEncryptor.java",
                    "ClearTextPasswordEncryptor$Modification",
                    "matches",
                    "apache_ftpserver_clear_unsafe/CorrectedMethod.java",
                    new String[] {"validPassword_public", "storedPassword_valid_secret1"},
                    new String[] {"validPassword_public", "storedPassword_invalid_secret2"}
                },
                {
                    "apache_ftpserver_md5_unsafe/Md5PasswordEncryptor.java",
                    "Md5PasswordEncryptor$Modification",
                    "regionMatches",
                    "apache_ftpserver_md5_unsafe/CorrectedMethod.java",
                    new String[] {"firstEncryption", "true", "0", "storedPassword_valid_secret1", "0", "firstEncryption.length()"},
                    new String[] {"firstEncryption", "true", "0", "storedPassword_invalid_secret2", "0", "firstEncryption.length()"}
                },
                {
                    "apache_ftpserver_salted_unsafe/SaltedPasswordEncryptor.java",
                    "SaltedPasswordEncryptor$Modification",
                    "matches",
                    "apache_ftpserver_salted_unsafe/CorrectedMethod.java",
                    new String[] {"password_public", "storedPassword_secret1"},
                    new String[] {"password_public", "storedPassword_secret2"}
                },
                {
                    "blazer_passwordEq_unsafe/User.java",
                    "User$Modification",
                    "passwordsEqual_unsafe",
                    "blazer_passwordEq_unsafe/CorrectedMethod.java",
                    new String[] {"secret1", "publicVal"},
                    new String[] {"secret2", "publicVal"}
                },
                {
                    "themis_dynatable_unsafe/SchoolCalendarServiceImpl.java",
                    "SchoolCalendarServiceImpl$Modification",
                    "getPeople_unsafe",
                    "themis_dynatable_unsafe/CorrectedMethod.java",
                    new String[] {"startIndex", "maxCount"},
                    new String[] {"startIndex2", "maxCount2"}
                },
                {
                    "themis_orientdb_unsafe/OSecurityManager.java",
                    "OSecurityManager$Modification",
                    "checkPassword_unsafe",
                    "themis_orientdb_unsafe/CorrectedMethod.java",
                    new String[] {"secret_iPassword1", "public_ihash"},
                    new String[] {"secret_iPassword2", "public_ihash"}
                },
                {
                    "themis_spring-security_unsafe/PasswordEncoderUtils.java",
                    "PasswordEncoderUtils$Modification",
                    "equals_unsafe",
                    "themis_spring-security_unsafe/CorrectedMethod.java",
                    new String[] {"secret1_expected", "public_actual"},
                    new String[] {"secret2_expected", "public_actual"}
                }
        };
    }

    @DataProvider
    /*
      String pathToVulnerableMethod,
      String correctedClassName,
      String methodName,
      String correctedMethodPath,
      String[] firstUseCaseArgumentsNames,
      String[] secondUseCaseArgumentsNames
     */
    private Object[][] correctVulnerableMethodWithControlFlow() {
        return new Object[][] {
                {
                    "blazer_array_unsafe/MoreSanity.java",
                    "MoreSanity$Modification",
                    "array_unsafe",
                    "blazer_array_unsafe/CorrectedMethod.java",
                    new String[] {"public_a", "secret1_taint"},
                    new String[] {"public_a", "secret2_taint"}
                },
                {
                    "blazer_gpt14_unsafe/GPT14.java",
                    "GPT14$Modification",
                    "modular_exponentiation_inline_unsafe",
                    "blazer_gpt14_unsafe/CorrectedMethod.java",
                    new String[] {"public_a", "secret1_b", "public_p"},
                    new String[] {"public_a", "secret2_b", "public_p"}
                },
                {
                    "blazer_k96_unsafe/K96.java",
                    "K96$Modification",
                    "modular_exponentiation_unsafe",
                    "blazer_k96_unsafe/CorrectedMethod.java",
                    new String[] {"public_y", "secret1_x", "public_n", "bitLength1"},
                    new String[] {"public_y", "secret2_x", "public_n", "bitLength2"}
                },
                {
                    "blazer_modpow1_unsafe/ModPow1.java",
                    "ModPow1$Modification",
                    "modPow1_unsafe",
                    "blazer_modpow1_unsafe/CorrectedMethod.java",
                    new String[] {"public_base", "secret1_exponent", "public_modulus", "bitLength1"},
                    new String[] {"public_base", "secret2_exponent", "public_modulus", "bitLength2"}
                },
                {
                    "blazer_straightline_unsafe/Sanity.java",
                    "Sanity$Modification",
                    "straightline_unsafe",
                    "blazer_straightline_unsafe/CorrectedMethod.java",
                    new String[] {"secret1_a", "public_b"},
                    new String[] {"secret2_a", "public_b"}
                },
                {
                    "blazer_unixlogin_unsafe/Timing.java",
                    "Timing$Modification",
                    "login_unsafe",
                    "blazer_unixlogin_unsafe/CorrectedMethod.java",
                    new String[] {"username_secret1", "password_secret1"},
                    new String[] {"username_secret2", "password_secret2"}
                },
                {
                    "stac_ibasys_unsafe/ImageMatcherWorker.java",
                    "ImageMatcherWorker$Modification",
                    "test",
                    "stac_ibasys_unsafe/CorrectedMethod.java",
                    new String[] {"public_guess", "secret1_pw"},
                    new String[] {"public_guess", "secret2_pw"}
                },
                {
                    "themis_pac4j_unsafe/DbAuthenticator.java",
                    "DbAuthenticator$Modification",
                    "validate_unsafe",
                    "themis_pac4j_unsafe/CorrectedMethod.java",
                    new String[] {"cred"},
                    new String[] {"cred2"}
                }
        };
    }

    @DataProvider
    /*
      String pathToVulnerableMethod,
      String correctedClassName,
      String methodName,
      String correctedMethodPath,
      String[] firstUseCaseArgumentsNames,
      String[] secondUseCaseArgumentsNames
     */
    private Object[][] correctVulnerableMethodWithMixedVulnerability() {
        return new Object[][] {
                {
                    "apache_ftpserver_salted_unsafe2/SaltedPasswordEncryptor.java",
                    "SaltedPasswordEncryptor$Modification",
                    "regionMatches",
                    "apache_ftpserver_salted_unsafe2/CorrectedMethod.java",
                    new String[] {"thisString1", "true", "0", "password_public", "0", "thisString1.length()"},
                    new String[] {"thisString2", "true", "0", "password_public", "0", "thisString2.length()"}
                },
                {
                    "apache_ftpserver_clear_unsafe2/ClearTextPasswordEncryptor.java",
                    "ClearTextPasswordEncryptor$Modification",
                    "isEqual_unsafe",
                    "apache_ftpserver_clear_unsafe2/CorrectedMethod.java",
                    new String[] {"validPassword_public", "storedPassword_valid_secret1"},
                    new String[] {"validPassword_public", "storedPassword_invalid_secret2"}
                },
                {
                    "apache_ftpserver_stringutils_unsafe/StringUtils.java",
                    "StringUtils$Modification",
                    "pad_unsafe",
                    "apache_ftpserver_stringutils_unsafe/CorrectedMethod.java",
                    new String[] {"userName1", "' '", "true", "MAX_USERNAME_LENGTH"},
                    new String[] {"userName2", "' '", "true", "MAX_USERNAME_LENGTH"}
                },
                {
                    "blazer_sanity_unsafe/Sanity.java",
                    "Sanity$Modification",
                    "sanity_unsafe",
                    "blazer_sanity_unsafe/CorrectedMethod.java",
                    new String[] {"secret1_a", "public_b"},
                    new String[] {"secret2_a", "public_b"}
                },
                {
                    "example_PWCheck_unsafe/PWCheck.java",
                    "PWCheck$Modification",
                    "pwcheck1_unsafe",
                    "example_PWCheck_unsafe/CorrectedMethod.java",
                    new String[] {"public_guess", "secret1_pw"},
                    new String[] {"public_guess", "secret2_pw"}
                },
                {
                    "github_authmreloaded_unsafe/UnsaltedMethod.java",
                    "UnsaltedMethod$Modification",
                    "isEqual_unsafe",
                    "github_authmreloaded_unsafe/CorrectedMethod.java",
                    new String[] {"storedPassword_valid_secret1.getHash()", "encrMethod.computeHash(password_public)"},
                    new String[] {"storedPassword_invalid_secret2.getHash()", "encrMethod.computeHash(password_public)"}
                },
                {
                    "themis_boot-stateless-auth_unsafe/TokenHandler.java",
                    "TokenHandler$Modification",
                    "unsafe_isEqual",
                    "themis_boot-stateless-auth_unsafe/CorrectedMethod.java",
                    new String[] {"userBytes1", "hash1"},
                    new String[] {"userBytes2", "hash2"}
                },
                {
                    "themis_jdk_unsafe/MessageDigest.java",
                    "MessageDigest$Modification",
                    "isEqual_unsafe",
                    "themis_jdk_unsafe/CorrectedMethod.java",
                    new String[] {"secret1_digesta", "public_digestb"},
                    new String[] {"secret2_digesta", "public_digestb"}
                },
                {
                    "themis_jetty_unsafe/Credential.java",
                    "Credential$Modification",
                    "stringEquals_original",
                    "themis_jetty_unsafe/CorrectedMethod.java",
                    new String[] {"publicCred", "secret1"},
                    new String[] {"publicCred", "secret2"}
                },
                {
                    "themis_oacc_unsafe/PasswordCredentials.java",
                    "PasswordCredentials$Modification",
                    "equals",
                    "themis_oacc_unsafe/CorrectedMethod.java",
                    new String[] {"secret1"},
                    new String[] {"secret2"}
                },
                {
                    "themis_oacc_unsafe2/PasswordCredentials.java",
                    "PasswordCredentials$Modification",
                    "ArraysIsEquals",
                    "themis_oacc_unsafe2/CorrectedMethod.java",
                    new String[] {"public_credentials.getPassword()", "secret1.getPassword()"},
                    new String[] {"public_credentials.getPassword()", "secret2.getPassword()"}
                },
                {
                    "themis_orientdb_unsafe2/OSecurityManager.java",
                    "OSecurityManager$Modification",
                    "equals_inline",
                    "themis_orientdb_unsafe2/CorrectedMethod.java",
                    new String[] {"secret_iPassword1", "public_ihash"},
                    new String[] {"secret_iPassword2", "public_ihash"}
                },
                {
                    "themis_picketbox_unsafe/UsernamePasswordLoginModule.java",
                    "UsernamePasswordLoginModule$Modification",
                    "validatePassword_unsafe",
                    "themis_picketbox_unsafe/CorrectedMethod.java",
                    new String[] {"secret1_expected", "public_actual"},
                    new String[] {"secret2_expected", "public_actual"}
                },
                {
                    "themis_picketbox_unsafe2/UsernamePasswordLoginModule.java",
                    "UsernamePasswordLoginModule$Modification",
                    "equals",
                    "themis_picketbox_unsafe2/CorrectedMethod.java",
                    new String[] {"secret1_expected", "public_actual"},
                    new String[] {"secret2_expected", "public_actual"}
                },
                {
                    "themis_tomcat_unsafe/DataSourceRealm.java",
                    "DataSourceRealm$Modification",
                    "authenticate_unsafe",
                    "themis_tomcat_unsafe/CorrectedMethod.java",
                    new String[] {"dbConnection", "secret_user1", "pw"},
                    new String[] {"dbConnection", "secret_user2", "pw"}
                }
        };
    }
    //endregion

    @Test(dataProvider = "findVulnerableMethodAndClass")
    public void testDiscoverMethod(String classpath, String expectedClassName, String expectedMethodName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(classpath);

        if (resource == null) {
            fail(String.format("Could not obtain the resource in location %s", classpath));
        }

        Optional<VulnerableMethodUses> optionalVulnerableMethodUsesCases = FindVulnerableMethod.processDriver(resource.getPath());

        if (!optionalVulnerableMethodUsesCases.isPresent()) {
            fail("Could not obtain the vulnerable method uses cases.");
        }

        VulnerableMethodUses vulnerableMethodUses = optionalVulnerableMethodUsesCases.get();

        if (!vulnerableMethodUses.isValid()) {
            fail("The vulnerable method uses cases is not valid.");
        }

        // First Vulnerable method use case
        String firstVulnerableClassName = vulnerableMethodUses.getFirstUseCaseClassName();
        String firstVulnerableMethodName = vulnerableMethodUses.getFirstUseCaseMethodName();
        String[] firstVulnerableMethodArguments = vulnerableMethodUses.getFirstUseCaseArgumentsNames();

        // Second Vulnerable method use case
        String secondVulnerableClassName = vulnerableMethodUses.getSecondUseCaseClassName();
        String secondVulnerableMethodName = vulnerableMethodUses.getSecondUseCaseMethodName();
        String[] secondVulnerableMethodArguments = vulnerableMethodUses.getSecondUseCaseArgumentsNames();

        Assert.assertEquals(firstVulnerableClassName, secondVulnerableClassName);
        Assert.assertEquals(firstVulnerableClassName, expectedClassName);
        Assert.assertEquals(firstVulnerableMethodName, secondVulnerableMethodName);
        Assert.assertEquals(firstVulnerableMethodName, expectedMethodName);
        Assert.assertEquals(firstVulnerableMethodArguments.length, secondVulnerableMethodArguments.length);
    }

    @Test(dataProvider = "invalidDrivers")
    public void testInvalidDrivers(String classpath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(classpath);

        if (resource == null) {
            fail(String.format("Could not obtain the resource in location %s", classpath));
        }

        Optional<VulnerableMethodUses> optionalVulnerableMethodUsesCases = FindVulnerableMethod.processDriver(resource.getPath());

        Assert.assertFalse(optionalVulnerableMethodUsesCases.isPresent());
    }

    @Test(dataProvider = "correctVulnerableMethodWithEarlyExit")
    public void testCorrectMethodWithEarlyExit(String pathToVulnerableMethod,
                                               String correctedClassName,
                                               String methodName,
                                               String correctedMethodPath,
                                               String[] firstUseCaseArgumentsNames,
                                               String[] secondUseCaseArgumentsNames) {

        runVulnerabilityCorrection(pathToVulnerableMethod, correctedClassName, methodName, correctedMethodPath, firstUseCaseArgumentsNames, secondUseCaseArgumentsNames);
    }

    @Test(dataProvider = "correctVulnerableMethodWithControlFlow")
    public void testCorrectMethodWithControlFlow(String pathToVulnerableMethod,
                                                 String correctedClassName,
                                                 String methodName,
                                                 String correctedMethodPath,
                                                 String[] firstUseCaseArgumentsNames,
                                                 String[] secondUseCaseArgumentsNames) {

        runVulnerabilityCorrection(pathToVulnerableMethod, correctedClassName, methodName, correctedMethodPath, firstUseCaseArgumentsNames, secondUseCaseArgumentsNames);
    }

    @Test(dataProvider = "correctVulnerableMethodWithMixedVulnerability")
    public void testCorrectMethodWithMixedVulnerability(String pathToVulnerableMethod,
                                                        String correctedClassName,
                                                        String methodName,
                                                        String correctedMethodPath,
                                                        String[] firstUseCaseArgumentsNames,
                                                        String[] secondUseCaseArgumentsNames) {

        runVulnerabilityCorrection(pathToVulnerableMethod, correctedClassName, methodName, correctedMethodPath, firstUseCaseArgumentsNames, secondUseCaseArgumentsNames);
    }

    private void runVulnerabilityCorrection(String pathToVulnerableMethod,
                                            String correctedClassName,
                                            String methodName,
                                            String correctedMethodPath,
                                            String[] firstUseCaseArgumentsNames,
                                            String[] secondUseCaseArgumentsNames) {
        // Setup
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL vulnerableMethodResource = classLoader.getResource(pathToVulnerableMethod);

        if (vulnerableMethodResource == null) {
            fail(String.format("Could not obtain the vulnerable method in the path %s.", pathToVulnerableMethod));
        }

        Launcher launcher = Setup.setupLauncher(vulnerableMethodResource.getPath(), "");
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        CtClass<?> vulnerableClass = model.filterChildren(new TypeFilter<>(CtClass.class)).first();
        CtMethod<?> vulnerableMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName)).first();
        vulnerableClass.setSimpleName(correctedClassName);
        VulnerableMethodUses vulnerableMethodUses = new VulnerableMethodUses();
        vulnerableMethodUses.setUseCase("", "", "", firstUseCaseArgumentsNames);
        vulnerableMethodUses.setUseCase("", "", "", secondUseCaseArgumentsNames);

        // Act
        Method modifyCode = null;
        try {
            modifyCode = ModificationOfCode.class.getDeclaredMethod("modifyCode", Factory.class, CtMethod.class, CtModel.class, VulnerableMethodUses.class);
        } catch (NoSuchMethodException e) {
            fail("Could not find the method modifyCode. All tests will have problems.");
        }

        modifyCode.setAccessible(true);

        try {
            modifyCode.invoke(null, factory, vulnerableMethod, model, vulnerableMethodUses);
        } catch (IllegalAccessException | InvocationTargetException e) {
            fail("Error invoking the modifyCode method. An error possibly occurred in the method 'modifyCode' or any of its sub-methods.");
        }

        // Assert
        URL resource = classLoader.getResource(correctedMethodPath);

        if (resource == null) {
            fail(String.format("Could not obtain the corrected method in the path %s", correctedMethodPath));
        }

        List<String> strings = null;
        try {
            strings = Files.readAllLines(Paths.get(resource.toURI()), StandardCharsets.US_ASCII);
        } catch (IOException | URISyntaxException e) {
            fail("An error occurred when trying to obtain the corrected method. Check possible error name of the file.");
        }

        CtMethod<?> correctedMethod = model.filterChildren(new TypeFilter<>(CtMethod.class)).select(new NameFilter<>(methodName + "$Modification")).first();
        List<String> correctedMethodList = Arrays.asList(correctedMethod.toString().split("\\r?\\n"));

        Iterator<String> expectedCorrectionIterator = strings.iterator();
        Iterator<String> actualCorrectionIterator = correctedMethodList.iterator();

        while (expectedCorrectionIterator.hasNext() && actualCorrectionIterator.hasNext()) {
            String expectedCorrectionLine = expectedCorrectionIterator.next().replace(" ", "");
            String actualCorrectionLine = actualCorrectionIterator.next().replace(" ", "");
            Assert.assertEquals(actualCorrectionLine, expectedCorrectionLine);
        }

        Assert.assertFalse(expectedCorrectionIterator.hasNext());
        Assert.assertFalse(actualCorrectionIterator.hasNext());
    }
}