public static boolean array_unsafe$Modification(int[] a, int taint) {
    int $2;
    System.out.println(a.length);
    int t;
    if (taint < 0) {
        int i = a.length - 1;
        while (i >= 0) {
            t = a[i];
            i--;
        }
        int $1 = 0;
        $2 = a[$1] / 2;
        $1 = a.length;
    } else {
        int i = 0;
        t = a[i] / 2;
        i = a.length;
        int $3 = a.length - 1;
        while ($3 >= 0) {
            $2 = a[$3];
            $3--;
        }
    }
    return false;
}