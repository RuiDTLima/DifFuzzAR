import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TestDifFuzzAR {
    @DataProvider
    private Object[][] getParameters() {
        return new Object[][] {
                {"apache_ftpserver_clear_safe\\Driver_Clear.java", "pe.matches"},
                {"apache_ftpserver_clear_unsafe\\Driver_Clear.java", "pe.matches"},
                {"apache_ftpserver_md5_safe\\Driver_MD5.java", "pe.matches"},
                {"apache_ftpserver_md5_unsafe\\Driver_MD5.java", "pe.matches"},
                {"apache_ftpserver_salted_encrypt_unsafe\\Driver_Salted_Encrypt.java", "pe.encrypt"},
                {"apache_ftpserver_salted_safe\\Driver_Salted.java", "pe.matches"},
                {"apache_ftpserver_salted_unsafe\\Driver_Salted.java", "pe.matches"},
                {"apache_ftpserver_stringutils_safe\\Driver_StringUtilsPad.java", "StringUtils.pad"},
                {"apache_ftpserver_stringutils_unsafe\\Driver_StringUtilsPad.java", "StringUtils.pad"},
                {"blazer_array_safe\\MoreSanity_Array_FuzzDriver.java", "MoreSanity.array_safe"},
                {"blazer_array_unsafe\\MoreSanity_Array_FuzzDriver.java", "MoreSanity.array_unsafe"},
                {"blazer_gpt14_safe\\GPT14_FuzzDriver.java", "GPT14.modular_exponentiation_safe"},
                {"blazer_gpt14_unsafe\\GPT14_FuzzDriver.java", "GPT14.modular_exponentiation_inline_unsafe"},
                {"blazer_k96_safe\\K96_FuzzDriver.java", "K96.modular_exponentiation_safe"},
                {"blazer_k96_unsafe\\K96_FuzzDriver.java", "K96.modular_exponentiation_unsafe"},
                {"blazer_loopandbranch_safe\\MoreSanity_LoopAndBranch_FuzzDriver.java", "MoreSanity.loopAndbranch_safe"},
                {"blazer_loopandbranch_unsafe\\MoreSanity_LoopAndBranch_FuzzDriver.java", "MoreSanity.loopAndbranch_unsafe"},
                {"blazer_modpow1_safe\\ModPow1_FuzzDriver.java", "ModPow1.modPow1_safe"},
                {"blazer_modpow1_unsafe\\ModPow1_FuzzDriver.java", "ModPow1.modPow1_unsafe"},
                {"blazer_modpow2_safe\\ModPow2_FuzzDriver.java", "ModPow2.modPow2_safe"},
                {"blazer_modpow2_unsafe\\ModPow2_FuzzDriver.java", "ModPow2.modPow2_unsafe"},
                {"blazer_passwordEq_safe\\User_FuzzDriver.java", "User.passwordsEqual_safe"},
                {"blazer_passwordEq_unsafe\\User_FuzzDriver.java", "User.passwordsEqual_unsafe"},
                {"blazer_sanity_safe\\Sanity_FuzzDriver.java", "Sanity.sanity_safe"},
                {"blazer_sanity_unsafe\\Sanity_FuzzDriver.java", "Sanity.sanity_unsafe"},
                {"blazer_straightline_safe\\Sanity_FuzzDriver.java", "Sanity.straightline_safe"},
                {"blazer_straightline_unsafe\\Sanity_FuzzDriver.java", "Sanity.straightline_unsafe"},
                {"blazer_unixlogin_safe\\Timing_FuzzDriver.java", "Timing.login_safe"},
                {"blazer_unixlogin_unsafe\\Timing_FuzzDriver.java", "Timing.login_unsafe"},
                {"example_PWCheck_safe\\Driver.java", "PWCheck.pwcheck3_safe"},
                {"example_PWCheck_unsafe\\Driver.java", "PWCheck.pwcheck1_unsafe"},
                {"github_authmreloaded_safe\\Driver.java", "encrMethod.comparePassword"},
                {"github_authmreloaded_unsafe\\Driver.java", "encrMethod.comparePassword"},
                {"stac_crime_unsafe\\CRIME_Driver.java", "LZ77T.compress"},
                {"stac_ibasys_unsafe\\ImageMatcher_FuzzDriver.java", "ImageMatcherWorker.test"},
                {"themis_boot-stateless-auth_safe\\Driver.java", "th.parseUserFromToken"},
                {"themis_boot-stateless-auth_unsafe\\Driver.java", "th.parseUserFromToken"},
                {"themis_dynatable_unsafe\\Driver.java", "ser1.getPeople"},
                {"themis_GWT_advanced_table_unsafe\\Driver.java", "ser1.getRows"},
                {"themis_jdk_safe\\MessageDigest_FuzzDriver.java", "MessageDigest.isEqual_safe"},
                {"themis_jdk_unsafe\\MessageDigest_FuzzDriver.java", "MessageDigest.isEqual_unsafe"},
                {"themis_jetty_safe\\Credential_FuzzDriver.java", "Credential.stringEquals_safe"},
                {"themis_jetty_unsafe\\Credential_FuzzDriver.java", "Credential.stringEquals_original"},
                {"themis_oacc_unsafe\\Driver.java", "public_credentials.equals"},
                {"themis_openmrs-core_unsafe\\Driver.java", "Security.hashMatches"},
                {"themis_orientdb_safe\\OSecurityManager_FuzzDriver.java", "manager.checkPassword_safe"},
                {"themis_orientdb_unsafe\\OSecurityManager_FuzzDriver.java", "manager.checkPassword_unsafe"},
                {"themis_pac4j_safe\\Driver.java", "dbAuth.validate_safe"},
                {"themis_pac4j_unsafe\\Driver.java", "dbAuth.validate_unsafe"},
                {"themis_pac4j_unsafe_ext\\Driver.java", "dbAuth.validate_unsafe"},
                {"themis_picketbox_safe\\UsernamePasswordLoginModule_FuzzDriver.java", "UsernamePasswordLoginModule.validatePassword_safe"},
                {"themis_picketbox_unsafe\\UsernamePasswordLoginModule_FuzzDriver.java", "UsernamePasswordLoginModule.validatePassword_unsafe"},
                {"themis_spring-security_safe\\PasswordEncoderUtils_FuzzDriver.java", "PasswordEncoderUtils.equals_safe"},
                {"themis_spring-security_unsafe\\PasswordEncoderUtils_FuzzDriver.java", "PasswordEncoderUtils.equals_unsafe"},
                {"themis_tomcat_safe\\Tomcat_FuzzDriver.java", "DSR.authenticate_safe"},
                {"themis_tomcat_unsafe\\Tomcat_FuzzDriver.java", "DSR.authenticate_unsafe"},
                {"themis_tourplanner_safe\\Driver.java", "ts.doGet"},
                {"themis_tourplanner_unsafe\\Driver.java", "ts.doGet"}
        };
    }

    @Test(dataProvider = "getParameters")
    public void testDiscoverMethod(String classpath, String expectedName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(classpath);
        if (inputStream == null) {
            Assert.fail();
        }

        String methodLineFound = DifFuzzAR.discoverMethod(new InputStreamReader(inputStream));

        if (methodLineFound == null)
            Assert.fail();

        String methodName;
        if (methodLineFound.contains("="))
            methodName = methodLineFound.subSequence(methodLineFound.indexOf("= ") + 2, methodLineFound.indexOf("(")).toString();
        else {
            methodLineFound = methodLineFound.replace(" ", "").replace("\t", "");
            methodName = methodLineFound.substring(0, methodLineFound.indexOf("("));
        }
        Assert.assertEquals(methodName, expectedName);
    }
}