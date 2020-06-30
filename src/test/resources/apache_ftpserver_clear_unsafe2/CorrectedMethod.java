public boolean isEqual_unsafe$Modification(String thisObject, Object otherObject) {
    boolean $1 = false;
    if (thisObject == otherObject) {
        $1 = true;
    }
    if (otherObject instanceof String) {
        String anotherString = ((String) (otherObject));
        int n = thisObject.length();
        if (n == anotherString.length()) {
            char[] v1 = thisObject.toCharArray();
            char[] v2 = anotherString.toCharArray();
            int i = 0;
            $1 = true;
            while ((n--) != 0) {
                if (((i < v1.length) && (i < v2.length)) && (v1[i] != v2[i])) {
                    $1 = false;
                }
                i++;
            }
        }
    }
    return $1;
}