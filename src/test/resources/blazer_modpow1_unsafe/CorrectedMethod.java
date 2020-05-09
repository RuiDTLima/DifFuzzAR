public static BigInteger modPow1_unsafe$Modification(BigInteger base, BigInteger exponent, BigInteger modulus, int width) {
    BigInteger $1;
    BigInteger s = BigInteger.valueOf(1);
    for (int i = 0; i < width; i++) {
        s = s.multiply(s).mod(modulus);
        if (exponent.testBit((width - i) - 1)) {
            s = standardMultiply(s, base).mod(modulus);
        } else {
            $1 = standardMultiply(s, base).mod(modulus);
        }
    }
    return s;
}