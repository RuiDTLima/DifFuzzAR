package util;

@FunctionalInterface
public interface ModifyOperationFunction<F, S, T, R> {
    R apply(F f, S s, T t);
}
