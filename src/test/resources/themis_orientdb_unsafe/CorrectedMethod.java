public boolean checkPassword_unsafe$Modification(final String iPassword, final String iHash) {
    boolean $1 = equals_inline(iPassword, iHash);
    if (iHash.startsWith(HASH_ALGORITHM_PREFIX)) {
        final String s = iHash.substring(HASH_ALGORITHM_PREFIX.length());
        $1 = createSHA256(iPassword).equals(s);
    } else if (iHash.startsWith(PBKDF2_ALGORITHM_PREFIX)) {
        final String s = iHash.substring(PBKDF2_ALGORITHM_PREFIX.length());
        $1 = checkPasswordWithSalt(iPassword, s, PBKDF2_ALGORITHM);
    } else if (iHash.startsWith(PBKDF2_SHA256_ALGORITHM_PREFIX)) {
        final String s = iHash.substring(PBKDF2_SHA256_ALGORITHM_PREFIX.length());
        $1 = checkPasswordWithSalt(iPassword, s, PBKDF2_SHA256_ALGORITHM);
    }
    return $1;
}