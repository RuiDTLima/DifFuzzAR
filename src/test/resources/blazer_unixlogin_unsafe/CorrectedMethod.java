public static boolean login_unsafe$Modification(String u, String p) {
    boolean $1 = false;
    boolean outcome = false;
    if (map.containsKey(u)) {
        if (map.get(u).equals(md5(p))) {
            outcome = true;
        }
    } else {
        String $2 = md5(p);
        String $3 = md5(p);
        if ($2.equals($3)) {
            $1 = true;
        }
    }
    return outcome;
}