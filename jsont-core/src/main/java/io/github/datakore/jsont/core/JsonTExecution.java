package io.github.datakore.jsont.core;

import io.github.datakore.jsont.chunk.DataRowRecord;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.pipeline.ConvertStage;
import io.github.datakore.jsont.pipeline.ParseStage;
import io.github.datakore.jsont.pipeline.ScanStage;
import io.github.datakore.jsont.pipeline.ValidateStage;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StepCounter;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.function.Consumer;

public class JsonTExecution {

    private final JsonTConfig config;
    private final InputStream stream;
    private final ChunkContext chunkContext;
    private Consumer<StepCounter> monitor;

    public JsonTExecution(JsonTConfig config, ChunkContext chunkContext, InputStream stream, Consumer<StepCounter> monitor) {
        this.config = config;
        this.stream = stream;
        this.monitor = monitor;
        this.chunkContext = chunkContext;
    }


    public Flux<RowNode> parse(int parallelism) {
        // 1. Scan
        ScanStage scanStage = new ScanStage(stream, chunkContext, monitor);

        Flux<DataRowRecord> rawRecords = scanStage.execute(Flux.empty());

        // 2. Parse
        ParseStage parseStage = new ParseStage(config.errorCollector, chunkContext, monitor, parallelism);
        return parseStage.execute(rawRecords);
    }

    public Flux<RowNode> validate(Class<?> targetType, int parallelism) {
        // 1 & 2. Scan & Parse
        Flux<RowNode> parsedRows = parse(parallelism);

        // 3. Validate
        ValidateStage validateStage = new ValidateStage(config.namespaceT, config.errorCollector, targetType, monitor, parallelism);
        return validateStage.execute(parsedRows);
    }

    public <T> Flux<T> convert(Class<T> targetType, int parallelism) {
        // 1, 2 & 3. Scan, Parse & Validate
        Flux<RowNode> validatedRows = validate(targetType, parallelism);

        // 4. Convert
        ConvertStage<T> convertStage = new ConvertStage<>(config.namespaceT, config.adapterRegistry, targetType, monitor, parallelism);
        return convertStage.execute(validatedRows);
    }
}
