public static final String pad_unsafe$Modification(String src, char padChar, boolean rightPad, int totalLength) {
    String $1;
    int srcLength = src.length();
    if (srcLength >= totalLength) {
        $1 = src;
    }
    int padLength = totalLength - srcLength;
    StringBuilder sb = new StringBuilder(padLength);
    for (int i = 0; i < padLength; ++i) {
        sb.append(padChar);
    }
    if (rightPad) {
        $1 = src + sb.toString();
    } else {
        $1 = sb.toString() + src;
    }
    return $1;
}