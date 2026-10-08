package com.example;

import java.util.function.Function;

public interface BoxDelegator<T> extends Box<T> {
    Box<T> getBoxDelegate();

    @Override
    default T get() {
        return getBoxDelegate().get();
    }

    @Override
    default <R> Box<R> map(Function<? super T, ? extends R> mapper) {
        return getBoxDelegate().map(mapper);
    }

    @Override
    default void set(T value) {
        getBoxDelegate().set(value);
    }
}
