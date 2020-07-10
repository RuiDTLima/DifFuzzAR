public boolean isEqual_unsafe$Modification(String thisObject, Object otherObject) {
    boolean $2 = false;
    boolean $1 = false;
    if (thisObject == otherObject) {
        $1 = true;
    } else {
        $2 = true;
    }
    if (otherObject instanceof String) {
        String anotherString = ((String) (otherObject));
        int n = thisObject.length();
        int $5 = n;
        if (n == anotherString.length()) {
            char[] v1 = thisObject.toCharArray();
            char[] v2 = anotherString.toCharArray();
            int i = 0;
            $1 = true;
            while ((n--) != 0) {
                if (((i < v1.length) && (i < v2.length)) && (v1[i] != v2[i])) {
                    $1 = false;
                } else {
                    $2 = false;
                }
                i++;
            }
        } else {
            char[] $3 = thisObject.toCharArray();
            int $4 = 0;
            $2 = true;
            while (($5--) != 0) {
                $4++;
            }
        }
    }
    return $1;
}