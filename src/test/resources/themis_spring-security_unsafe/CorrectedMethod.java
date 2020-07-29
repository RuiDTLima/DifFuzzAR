static boolean equals_unsafe$Modification(String expected, String actual) {
    int $3 = 0;
    boolean $2;
    boolean $1;
    byte[] expectedBytes = bytesUtf8(expected);
    byte[] actualBytes = bytesUtf8(actual);
    int expectedLength = (expectedBytes == null) ? -1 : expectedBytes.length;
    int actualLength = (actualBytes == null) ? -1 : actualBytes.length;
    if (expectedLength != actualLength) {
        $1 = false;
    } else {
        $2 = false;
    }
    int result = 0;
    for (int i = 0; i < expectedLength; i++) {
        if ((i < expectedLength) && (i < actualLength)) {
            result |= expectedBytes[i] ^ actualBytes[i];
        } else {
            $3 |= 0;
        }
    }
    $1 = result == 0;
    return $1;
}