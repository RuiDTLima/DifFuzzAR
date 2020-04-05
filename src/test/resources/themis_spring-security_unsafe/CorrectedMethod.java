static boolean equals_unsafe$Modification(String expected, String actual) {
    boolean $1;
    byte[] expectedBytes = bytesUtf8(expected);
    byte[] actualBytes = bytesUtf8(actual);
    int expectedLength = (expectedBytes == null) ? -1 : expectedBytes.length;
    int actualLength = (actualBytes == null) ? -1 : actualBytes.length;
    if (expectedLength != actualLength) {
        $1 = false;
    }
    int result = 0;
    for (int i = 0; i < expectedLength; i++) {
        result |= expectedBytes[i] ^ actualBytes[i];
    }
    $1 = result == 0;
    return $1;
}