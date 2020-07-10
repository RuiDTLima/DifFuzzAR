protected static boolean validatePassword_unsafe$Modification(String inputPassword, String expectedPassword) {
    boolean $1 = false;
    boolean valid = false;
    if ((inputPassword == null) || (expectedPassword == null)) {
        valid = false;
    } else {
        $1 = false;
    }
    valid = equals(inputPassword, expectedPassword);
    return valid;
}