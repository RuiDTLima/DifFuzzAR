public static boolean sanity_unsafe$Modification(int a, int b) {
    boolean $2 = false;
    boolean $1 = false;
    int i = b;
    int $3 = i;
    int j = b;
    if (b < 0) {
        $1 = false;
    }
    if (a < 0) {
        while ($3 > 0) {
            $3--;
        }
        $1 = true;
    } else {
        $2 = true;
        while (i > 0) {
            i--;
        }
    }
    return $1;
}