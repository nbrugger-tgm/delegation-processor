package com.example;

public interface GreeterDelegator extends Greeter {
    Greeter getGreeterDelegate();

    @Override
    default String greet(String name) {
        return getGreeterDelegate().greet(name);
    }
}
