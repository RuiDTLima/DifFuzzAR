package util;

@FunctionalInterface
public interface EqualStatementsFunction<F, S> {
    boolean apply(F f, S s);
}
