public boolean matches$Modification(String passwordToCheck, String storedPassword) {
    boolean $1;
    if (storedPassword == null) {
        throw new NullPointerException("storedPassword can not be null");
    }
    if (passwordToCheck == null) {
        throw new NullPointerException("passwordToCheck can not be null");
    }
    if (safeMode) {
        $1 = PasswordUtil.secureCompare(passwordToCheck, storedPassword, 1024);
    } else {
        $1 = isEqual_unsafe(passwordToCheck, storedPassword);
    }
    return $1;
}