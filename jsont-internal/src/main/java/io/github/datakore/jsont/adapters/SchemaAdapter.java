package io.github.datakore.jsont.adapters;

import java.util.List;

public interface SchemaAdapter<T> {
    Class<T> logicalType();

    T createTarget();

    void set(Object target, String fieldName, Object valuee);

    Object get(Object target, String fieldName);

}
