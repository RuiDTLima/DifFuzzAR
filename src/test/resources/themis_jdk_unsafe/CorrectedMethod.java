public static boolean isEqual_unsafe$Modification(byte[] digesta, byte[] digestb) {
    boolean $2 = true;
    boolean $1 = true;
    if (digesta.length != digestb.length) {
        $1 = false;
    } else {
        $2 = false;
    }
    for (int i = 0; i < digestb.length; i++) {
        if (((i < digesta.length) && (i < digestb.length)) && (digesta[i] != digestb[i])) {
            $1 = false;
        } else {
            $2 = false;
        }
    }
    return $1;
}