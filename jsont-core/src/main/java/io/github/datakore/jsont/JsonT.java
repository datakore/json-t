package io.github.datakore.jsont;

import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.core.JsonTConfigBuilder;

public final class JsonT {
    private JsonT() {
    }

    // 1. Simple Entry
    public static JsonTConfig configure() {
        return new JsonTConfigBuilder().build();
    }

    // 2. Custom Entry
    public static JsonTConfigBuilder configureBuilder() {
        return new JsonTConfigBuilder();
    }
}
