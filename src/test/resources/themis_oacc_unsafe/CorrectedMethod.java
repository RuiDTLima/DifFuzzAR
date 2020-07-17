@Override
public boolean equals$Modification(Object other) {
    boolean $2;
    boolean $1;
    if (this == other) {
        $1 = true;
    } else {
        $2 = true;
    }
    if ((other == null) || (getClass() != other.getClass())) {
        $1 = false;
    } else {
        $2 = false;
    }
    Impl impl = ((Impl) (other));
    $1 = ArraysIsEquals(password, impl.password);
    return $1;
}