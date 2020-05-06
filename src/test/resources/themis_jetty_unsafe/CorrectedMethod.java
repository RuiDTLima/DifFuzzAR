public static boolean stringEquals_original$Modification(String s1, String s2) {
    boolean $1 = false;
    if (s1 == s2) {
        $1 = true;
    }
    int n = s1.length();
    if (n == s2.length()) {
        char[] v1 = s1.toCharArray();
        char[] v2 = s2.toCharArray();
        int i = 0;
        $1 = true;
        while ((n--) != 0) {
            if (i < v1.length && i < v2.length && v1[i] != v2[i]) {
                $1 = false;
            }
            i++;
        }
        return $1;
    }
    return $1;
}