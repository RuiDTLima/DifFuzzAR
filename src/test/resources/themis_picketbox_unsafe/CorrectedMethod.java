public static boolean equals$Modification(String a, String b) {
    boolean $1 = false;
    if (a == b) {
        $1 = true;
    }
    int n = a.length();
    if (n == b.length()) {
        char[] v1 = a.toCharArray();
        char[] v2 = b.toCharArray();
        int i = 0;
        int j = 0;
        while ((n--) != 0) {
            if (i++ < v1.length && j++ < v2.length && v1[i++] != v2[j++]) {
                $1 = false;
            }
        }
        return $1;//$1 = true;
    }
    return $1;
}