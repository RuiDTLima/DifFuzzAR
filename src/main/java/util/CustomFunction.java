package util;

@FunctionalInterface
public interface CustomFunction<F, S, T, L> {
    void apply(F f, S s, T t, L l);
}
