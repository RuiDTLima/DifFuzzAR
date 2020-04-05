public static boolean sanity_unsafe$Modification(int a, int b) {
    boolean $1 = false;
    int i = b;
    int j = b;
    if (b < 0) {
        $1 = false;
    }
    if (a < 0) {
        $1 = true;
    } else {
        while (i > 0) {
            i--;
        }
    }
    return $1;
}