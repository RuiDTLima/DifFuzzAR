public static boolean unsafe_isEqual$Modification(byte[] a, byte[] a2) {
    boolean $2 = true;
    int $3 = 0;
    boolean $1 = true;
    if (a == a2) {
        $1 = true;
    } else {
        $2 = true;
    }
    if ((a == null) || (a2 == null)) {
        $1 = false;
    } else {
        $2 = false;
    }
    int length = 0;
    if (a != null) {
        length = a.length;
    } else {
        $3 = 0;
    }
    if ((a2 != null) && (a2.length != length)) {
        $1 = false;
    } else {
        $2 = false;
    }
    for (int i = 0; i < length; i++) {
        if ((((a != null) && (i < a.length)) && ((a2 != null) && (i < a2.length))) && (a[i] != a2[i])) {
            $1 = false;
        } else {
            $2 = false;
        }
    }
    return $1;
}