public static boolean equals$Modification(String a, String b) {
    boolean $2 = false;
    boolean $1 = false;
    if (a == b) {
        $1 = true;
    } else {
        $2 = true;
    }
    int n = a.length();
    int $6 = n;
    if (n == b.length()) {
        char[] v1 = a.toCharArray();
        char[] v2 = b.toCharArray();
        int i = 0;
        int j = 0;
        $1 = true;
        while ((n--) != 0) {
            if (((i < v1.length) && (j < v2.length)) && (v1[i++] != v2[j++])) {
                $1 = false;
            } else {
                $2 = false;
            }
        }
    } else {
        char[] $3 = a.toCharArray();
        int $4 = 0;
        int $5 = 0;
        $2 = true;
        while (($6--) != 0) {
        }
    }
    return $1;
}