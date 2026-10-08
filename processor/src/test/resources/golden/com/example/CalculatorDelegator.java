package com.example;

import java.io.IOException;

public interface CalculatorDelegator extends Calculator {
    Calculator getCalculatorDelegate();

    @Override
    default double divide(double a, double b) throws ArithmeticException, IOException {
        return getCalculatorDelegate().divide(a, b);
    }

    @Override
    default String[] history() {
        return getCalculatorDelegate().history();
    }

    @Override
    default void reset() {
        getCalculatorDelegate().reset();
    }

    @Override
    default int sum(int... values) {
        return getCalculatorDelegate().sum(values);
    }
}
