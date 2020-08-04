package util;

@FunctionalInterface
public interface EarlyExitFunction<F, S, T, Fr, Ft, Sx, Sp, R> {
    R apply(F factory, S iterator, T variable, Fr expression, Ft afterCycle, Sx block, Sp statement);
}
