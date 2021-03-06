public static BigInteger modular_exponentiation_unsafe$Modification(BigInteger y, BigInteger x, BigInteger n, int w) {
    BigInteger $1 = BigInteger.ZERO;
    BigInteger s = BigInteger.ONE;
    BigInteger r = BigInteger.ZERO;
    for (int k = 0; k < w; k++) {
        if (x.testBit(k)) {
            $1 = s;
            r = standardMultiply(s, y).mod(n);
        } else {
            $1 = standardMultiply(s, y).mod(n);
            r = s;
        }
        s = standardMultiply(r, r).mod(n);
    }
    return r;
}