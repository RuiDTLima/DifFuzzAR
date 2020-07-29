public boolean regionMatches$Modification(String thisValue, boolean ignoreCase, int toffset, String other, int ooffset, int len) {
    boolean $2 = true;
    boolean $1 = true;
    char[] ta = thisValue.toCharArray();
    int to = toffset;
    char[] pa = other.toCharArray();
    int po = ooffset;
    if ((((ooffset < 0) || (toffset < 0)) || (toffset > (((long) (thisValue.length())) - len))) || (ooffset > (((long) (other.length())) - len))) {
        $1 = false;
    } else {
        $2 = false;
    }
    while ((len--) > 0) {
        char c1 = ta[to++];
        char c2 = pa[po++];
        if (c1 == c2) {
            continue;
        }
        if (ignoreCase) {
            char u1 = Character.toUpperCase(c1);
            char u2 = Character.toUpperCase(c2);
            if (u1 == u2) {
                continue;
            }
            if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                continue;
            }
        }
        $1 = false;
    }
    return $1;
}