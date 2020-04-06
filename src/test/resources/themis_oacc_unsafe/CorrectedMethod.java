@Override
public boolean equals$Modification(Object other) {
    boolean $1;
    if (this == other) {
        $1 = true;
    }
    if ((other == null) || (getClass() != other.getClass())) {
        $1 = false;
    }
    Impl impl = ((Impl) (other));
    $1 = ArraysIsEquals(password, impl.password);
    return $1;
}