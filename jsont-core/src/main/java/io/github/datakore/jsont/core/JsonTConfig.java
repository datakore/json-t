package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.stringify.JsonTStringify;
import io.github.datakore.jsont.stringify.StringifyMode;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JsonTConfig {
    final NamespaceT namespaceT;
    final ErrorCollector errorCollector;
    final AdapterRegistry adapterRegistry;
    final int bufferSize;
    final Path errorFile;


    public JsonTConfig(NamespaceT namespaceT, ErrorCollector errorCollector, AdapterRegistry adapterRegistry, int bufferSize, Path errorFile) {
        this.namespaceT = namespaceT;
        this.errorCollector = errorCollector;
        this.adapterRegistry = adapterRegistry;
        this.bufferSize = bufferSize;
        this.errorFile = errorFile;
    }

    public JsonTExecution source(Path path) {
        Objects.requireNonNull(path, "Source cannot be null");
        try {
            return new JsonTExecution(this, CharStreams.fromPath(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonTExecution source(CharStream stream) {
        Objects.requireNonNull(stream, "Source cannot be null");
        return new JsonTExecution(this, stream);
    }

    public String stringify(Class<?> clazz) {
        JsonTStringify stringify = new JsonTStringify(adapterRegistry, namespaceT);
        return stringify.stringifySchema(clazz);
    }

    public String stringify(Object object) {
        return stringify(object, StringifyMode.DATA_ONLY);
    }

    public String stringify(Object object, StringifyMode mode) {
        List<Object> list = new ArrayList<>(1);
        list.add(object);
        return stringify(list, mode);
    }

    public String stringify(List<Object> list) {
        return stringify(list, StringifyMode.DATA_ONLY);
    }

    public String stringify(List<Object> list, StringifyMode mode) {
        JsonTStringify stringify = new JsonTStringify(adapterRegistry, namespaceT);
        return stringify.stringifyData(list, mode);
    }


}
