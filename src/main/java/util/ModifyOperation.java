package util;

@FunctionalInterface
public interface ModifyOperation<F, S, T, R> {
    R apply(F f, S s, T t);
}
