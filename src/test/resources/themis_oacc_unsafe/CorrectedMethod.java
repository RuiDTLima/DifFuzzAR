@Override
public boolean equals$Modification(Object other) {
    boolean $2;
    boolean $1;
    if (PasswordCredentials.Impl.this == other) {
        $1 = true;
    } else {
        $2 = true;
    }
    if ((other == null) || (getClass() != other.getClass())) {
        $1 = false;
    } else {
        $2 = false;
    }
    PasswordCredentials.Impl impl = ((PasswordCredentials.Impl) (other));
    $1 = ArraysIsEquals(password, impl.password);
    return $1;
}