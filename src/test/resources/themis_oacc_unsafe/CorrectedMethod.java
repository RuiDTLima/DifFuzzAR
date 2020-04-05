public boolean equals$Modification(Object other) {
    boolean $1;
    if (PasswordCredentials.Impl.this == other) {
        $1 = true;
    }
    if ((other == null) || (getClass() != other.getClass())) {
        $1 = false;
    }
    PasswordCredentials.Impl impl = ((PasswordCredentials.Impl) (other));
    $1 = ArraysIsEquals(password, impl.password);
    return $1;
}