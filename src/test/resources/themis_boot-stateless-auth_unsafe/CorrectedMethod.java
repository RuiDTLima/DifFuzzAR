public User parseUserFromToken_unsafe$Modification(String token) {
    User $1 = null;
    final String[] parts = token.split(SEPARATOR_SPLITTER);
    if (((parts.length == 2) && (parts[0].length() > 0)) && (parts[1].length() > 0)) {
        try {
            final byte[] userBytes = fromBase64(parts[0]);
            final byte[] hash = fromBase64(parts[1]);
            boolean validHash = unsafe_isEqual(createHmac(userBytes), hash);
            if (validHash) {
                final User user = fromJSON(userBytes);
                if (new Date().getTime() < user.getExpires()) {
                    $1 = user;
                }
                $1 = user;
            }
        } catch (IllegalArgumentException e) {
        }
    }
    return $1;
}