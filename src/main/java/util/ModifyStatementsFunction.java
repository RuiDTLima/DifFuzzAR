package util;

@FunctionalInterface
public interface ModifyStatementsFunction<F, S, T, Fr, L, R> {
    R apply(F f, S s, T t, Fr fr, L l);
}
