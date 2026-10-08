package com.example.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Trivial in-memory {@link Repository} used as the delegate in the integration tests. */
public final class InMemoryRepository implements Repository<String> {

    private final List<String> entities = new ArrayList<>();

    @Override
    public Optional<String> findById(String id) {
        return entities.stream().filter(entity -> entity.equals(id)).findFirst();
    }

    @Override
    public List<String> findAll() {
        return List.copyOf(entities);
    }

    @Override
    public void save(String entity) {
        entities.add(entity);
    }

    @Override
    public int count() {
        return entities.size();
    }
}
