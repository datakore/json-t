package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;

import java.nio.file.Path;

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


}
