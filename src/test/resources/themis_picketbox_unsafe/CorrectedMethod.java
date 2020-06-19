protected static boolean validatePassword_unsafe$Modification(String inputPassword, String expectedPassword) {
    boolean valid = false;
    if ((inputPassword == null) || (expectedPassword == null)) {
        valid = false;
    }
    valid = equals(inputPassword, expectedPassword);
    return valid;
}