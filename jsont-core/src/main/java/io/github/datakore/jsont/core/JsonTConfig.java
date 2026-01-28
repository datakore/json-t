package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.file.JsonTStructureAnalyzer;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StepCounter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public final class JsonTConfig {
    final NamespaceT namespaceT;
    ChunkContext context;
    final ErrorCollector errorCollector;
    final AdapterRegistry adapterRegistry;
    final int bufferSize;
    final Path errorFile;
    private Consumer<StepCounter> monitor;


    public JsonTConfig(ChunkContext context, ErrorCollector errorCollector, AdapterRegistry adapterRegistry, int bufferSize, Path errorFile) {
        this.context = context;
        this.namespaceT = context != null ? context.getNamespace() : null;
        this.errorCollector = errorCollector;
        this.adapterRegistry = adapterRegistry;
        this.bufferSize = bufferSize;
        this.errorFile = errorFile;
    }

    public JsonTExecution source(Path path, ChunkContext context) throws IOException {
        this.context = context;
        return source(path);
    }

    public JsonTExecution source(Path path) throws IOException {
        Objects.requireNonNull(path, "Source cannot be null");
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        AnalysisResult result = analyzer.analyze(path);
        ChunkContext _context = this.context;
        if (result.getVariant() == AnalysisResult.FileVariant.FRAGMENT) {
            assert _context != null;
            assert _context.getNamespace() != null;
            assert _context.getDataSchema() != null;
        } else if (result.getVariant() == AnalysisResult.FileVariant.DATA_BLOCK) {
            assert _context != null;
            assert _context.getNamespace() != null;
            SchemaModel sm = _context.getNamespace().findSchema(result.getDataSchemaName());
            assert sm != null;
            _context = new ChunkContext(_context.getNamespace(), sm, result.getDataStartOffset());
        } else if (result.getVariant() == AnalysisResult.FileVariant.FULL_DOCUMENT) {
            NamespaceT ns = ParserExecutor.validateSchema(result, errorCollector);
            _context = ParserExecutor.validateDataSchema(result, ns);
        } else {
            throw new SchemaException("Data is not provided in the input file");
        }
        return new JsonTExecution(this, _context, path, this.monitor);
    }

    public <T> String stringify(Class<T> clazz) {
        StreamingJsonTWriter<T> writer = new StreamingJsonTWriterBuilder<T>()
                .registry(this.adapterRegistry)
                .namespace(this.namespaceT)
                .build(clazz.getSimpleName());
        StringWriter sw = new StringWriter();
        writer.stringify(sw, clazz);
        return sw.toString();
    }

    public AdapterRegistry getAdapters() {
        return this.adapterRegistry;
    }

    public NamespaceT getNamespace() {
        return this.namespaceT;
    }
}
