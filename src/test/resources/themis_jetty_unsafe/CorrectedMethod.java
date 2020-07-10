public static boolean stringEquals_original$Modification(String s1, String s2) {
    boolean $2 = false;
    boolean $1 = false;
    if (s1 == s2) {
        $1 = true;
    } else {
        $2 = true;
    }
    int n = s1.length();
    int $5 = n;
    if (n == s2.length()) {
        char[] v1 = s1.toCharArray();
        char[] v2 = s2.toCharArray();
        int i = 0;
        $1 = true;
        while ((n--) != 0) {
            if (((i < v1.length) && (i < v2.length)) && (v1[i] != v2[i])) {
                $1 = false;
            } else {
                $2 = false;
            }
            i++;
        }
    } else {
        char[] $3 = s1.toCharArray();
        int $4 = 0;
        $2 = true;
        while (($5--) != 0) {
            $4++;
        }
    }
    return $1;
}