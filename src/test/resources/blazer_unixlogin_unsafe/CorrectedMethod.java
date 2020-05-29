public static boolean login_unsafe$Modification(String u, String p) {
    boolean $3 = false;
    boolean outcome = false;
    if (map.containsKey(u)) {
        if (map.get(u).equals(md5(p))) {
            outcome = true;
        }
    } else {
        String $1 = md5(p);
        String $2 = $1;
        if ($1.equals($2)) {
            $3 = true;
        }
    }
    return outcome;
}