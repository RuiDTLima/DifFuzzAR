public static final String pad_unsafe$Modification(String src, char padChar, boolean rightPad, int totalLength) {
    int $5 = 0;
    int $4 = src.length();
    int $3 = totalLength;
    String $2;
    String $1;
    int srcLength = src.length();
    StringBuilder $6 = new StringBuilder(srcLength);
    if (srcLength >= totalLength) {
        $1 = src;
    } else {
        $2 = src;
    }
    int padLength = 0;
    if (!(srcLength >= totalLength)) {
        padLength = totalLength - srcLength;
    } else {
        $5 = $3 - $4;
    }
    StringBuilder sb = new StringBuilder(padLength);
    for (int i = 0; i < totalLength; ++i) {
        if (i < padLength) {
            sb.append(padChar);
        } else {
            $6.append(padChar);
        }
    }
    if (rightPad) {
        $1 = src + sb.toString();
    } else {
        $1 = sb.toString() + src;
    }
    return $1;
}