public /*private*/ String encrypt$Modification(String password, String salt) {
    long tmpCost = Mem.instrCost;
    String hash = salt + password;
    for (int i = 0; i < HASH_ITERATIONS; i++) {
        hash = EncryptUtils.encryptMD5(hash);
    }
    return salt + ":" + hash;
}