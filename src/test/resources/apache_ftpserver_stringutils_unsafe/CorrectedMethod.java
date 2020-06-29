public static final String pad_unsafe$Modification(String src, char padChar, boolean rightPad, int totalLength) {
    String $2;
    String $1;
    int srcLength = src.length();
    StringBuilder $3 = new StringBuilder(srcLength);
    if (srcLength >= totalLength) {
        $1 = src;
    } else {
        $2 = src;
    }
    int padLength = totalLength - srcLength;
    StringBuilder sb = new StringBuilder(padLength);
    for (int i = 0; i < totalLength; ++i) {
        if (i < padLength) {
            sb.append(padChar);
        } else {
            $3.append(padChar);
        }
    }
    if (rightPad) {
        $1 = src + sb.toString();
    } else {
        $1 = sb.toString() + src;
    }
    return $1;
}