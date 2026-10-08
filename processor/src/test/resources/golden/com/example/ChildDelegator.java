package com.example;

public interface ChildDelegator extends Child {
    Child getChildDelegate();

    @Override
    default void base() {
        getChildDelegate().base();
    }

    @Override
    default void left() {
        getChildDelegate().left();
    }

    @Override
    default void right() {
        getChildDelegate().right();
    }
}
