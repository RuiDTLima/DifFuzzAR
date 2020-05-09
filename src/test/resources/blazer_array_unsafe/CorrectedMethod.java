public static boolean array_unsafe(int a[], int taint) {
    int t;
    if (taint < 0) {
        int i = a.length-1;
        while (i >= 0) {
            t = a[i];
            i--;
        }
    } else {
        int i = 0;
        while (i < a.length) {
            t = a[i] / 2;
            i++;
        }
    }
    return false;
}