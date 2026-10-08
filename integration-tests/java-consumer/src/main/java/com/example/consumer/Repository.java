package com.example.consumer;

import java.util.List;
import java.util.Optional;

/** A small generic interface used to exercise the generated delegators end to end. */
public interface Repository<T> {

    Optional<T> findById(String id);

    List<T> findAll();

    void save(T entity);

    int count();

    default boolean isEmpty() {
        return count() == 0;
    }
}
