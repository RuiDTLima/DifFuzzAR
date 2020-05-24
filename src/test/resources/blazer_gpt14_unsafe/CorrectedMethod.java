public static BigInteger modular_exponentiation_inline_unsafe$Modification(BigInteger a, BigInteger b, BigInteger p) {
    BigInteger $1 = BigInteger.valueOf(1);
    BigInteger m = BigInteger.valueOf(1);
    int n = b.bitLength();
    for (int i = 0; i < n; i++) {
        BigInteger p1;
        if (m.testBit(0)) {
            p1 = m;
        } else {
            p1 = BigInteger.valueOf(0);
        }
        int n1 = m.bitLength();
        for (int j = 1; j < n1; j++) {
            if (m.testBit(j)) {
                p = p.add(m);
            }
        }
        BigInteger t = m.multiply(a).mod(p);
        if (b.testBit(i)) {
            m = t;
        } else {
            $1 = t;
        }
    }
    return a;
}