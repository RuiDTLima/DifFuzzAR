public boolean equals_inline$Modification(String iPassword, String iHash) {
    boolean $4 = false;
    boolean $1 = false;
    int n = iPassword.length();
    if (n == iHash.length()) {
        char[] v1 = iPassword.toCharArray();
        char[] v2 = iHash.toCharArray();
        int i = 0;
        $1 = true;
        while ((n--) != 0) {
            if (((i < v1.length) && (i < v2.length)) && (v1[i] != v2[i])) {
                $1 = false;
            } else {
                $4 = false;
            }
            i++;
        }
    } else {
        char[] $2 = iPassword.toCharArray();
        int $3 = 0;
        $4 = true;
        while ((n--) != 0) {
            $3++;
        }
    }
    return $1;
}