public static boolean isEqual_unsafe$Modification(byte[] digesta, byte[] digestb) {
    boolean $1 = true;
    if (digesta.length != digestb.length) {
        $1 = false;
    }
    for (int i = 0; i < digesta.length; i++) {
        if (((i < digesta.length) && (i < digestb.length)) && (digesta[i] != digestb[i])) {
            $1 = false;
        }
    }
    return $1;
}