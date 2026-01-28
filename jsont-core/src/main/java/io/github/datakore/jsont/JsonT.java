package io.github.datakore.jsont;

import io.github.datakore.jsont.core.JsonTConfigBuilder;

public final class JsonT {
    private JsonT() {
    }

    // 2. Custom Entry
    public static JsonTConfigBuilder configureBuilder() {
        return new JsonTConfigBuilder();
    }
}
