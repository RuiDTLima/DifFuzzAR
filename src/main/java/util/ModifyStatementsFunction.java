package util;

@FunctionalInterface
public interface ModifyStatementsFunction<F, S, T, L, R> {
    R apply(F f, S s, T t, L l);
}
