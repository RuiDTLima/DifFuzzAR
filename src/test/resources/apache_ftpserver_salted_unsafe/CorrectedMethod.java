public boolean matches$Modification(String passwordToCheck, String storedPassword) {
    boolean $1;
    if (storedPassword == null) {
        throw new NullPointerException("storedPassword can not be null");
    }
    if (passwordToCheck == null) {
        throw new NullPointerException("passwordToCheck can not be null");
    }
    int divider = storedPassword.indexOf(':');
    if (divider < 1) {
        throw new IllegalArgumentException("stored password does not contain salt");
    }
    String storedSalt = storedPassword.substring(0, divider);
    if (safeMode) {
        $1 = PasswordUtil.secureCompare(encrypt(passwordToCheck, storedSalt), storedPassword, 1024);
    } else {
        $1 = equalsIgnoreCase(encrypt(passwordToCheck, storedSalt), storedPassword);
    }
    return $1;
}