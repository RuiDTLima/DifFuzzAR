public static boolean pwcheck1_unsafe$Modification(byte[] guess, byte[] pw) {
    boolean $1 = true;
    if (guess.length != pw.length) {
        $1 = false;
    }
    int i;
    for (i = 0; i < guess.length; i++) {
        if (((i < guess.length) && (i < pw.length)) && (guess[i] != pw[i])) {
            $1 = false;
        }
    }
    return $1;
}