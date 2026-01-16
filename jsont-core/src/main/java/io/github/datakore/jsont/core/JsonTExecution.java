package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.execution.CSVErrorLogger;
import io.github.datakore.jsont.execution.DataPipeline;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.types.ObjectType;
import io.github.datakore.jsont.parser.DataRowVisitor;
import org.antlr.v4.runtime.CharStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonTExecution {

    private final JsonTConfig config;
    private final CharStream stream;

    public JsonTExecution(JsonTConfig config, CharStream stream) {
        this.config = config;
        this.stream = stream;
    }

    private static void validateRecord(Map<String, Object> map, SchemaModel schema) {
        long requiredCount = schema.fields().stream()
                .filter(f -> !f.isFieldOptional())
                .count();

        int actualCount = map.size();
        int maxCount = schema.fields().size();

        // Check 1: Underflow (Missing required fields)
        if (actualCount < requiredCount) {
            throw new SchemaException(String.format(
                    "Data row underflow for %s: Expected at least %d fields, got %d",
                    schema.name(), requiredCount, actualCount));
        }

        // Check 2: Overflow (Extra fields not in schema)
        if (actualCount > maxCount) {
            throw new SchemaException(String.format(
                    "Data row overflow for %s: Expected max %d fields, got %d",
                    schema.name(), maxCount, actualCount));
        }
    }

    public Flux<Map<String, Object>> stream(int parallelism) {
        final DataPipeline pipeline = new DataPipeline(config.bufferSize, java.time.Duration.ofSeconds(1), new CSVErrorLogger(config.errorFile));
        DataRowVisitor rowListener = new DataRowVisitor(config.errorCollector, config.namespaceT, pipeline);

        Mono.fromRunnable(() -> {
                    try {
                        ParserExecutor.executeDataParse(stream, config.errorCollector, rowListener);
                    } catch (Exception e) {
                        List<ValidationError> list = new ArrayList<>(1);
                        ErrorLocation errorLoc = new ErrorLocation("Fatal error occured during parsing");
                        list.add(new ValidationError(Severity.FATAL, "Fatal Exception", errorLoc));
                        pipeline.onRowError(-1, list);
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        return pipeline.rows()
                .parallel(parallelism)           // Split into N groups
                .runOn(Schedulers.parallel())    // Run on Parallel Scheduler
                .sequential();
    }

    @SuppressWarnings("unchecked")
    public <T> Flux<T> as(Class<T> targetType, int parallelism) {
        final SchemaAdapter<?> adapter = config.adapterRegistry.resolve(targetType.getSimpleName());
        if (adapter == null) {
            throw new DataException("No adapter found for type " + targetType.getName());
        }
        final SchemaModel schema = config.namespaceT.findSchema(targetType.getSimpleName());
        if (schema == null) {
            throw new DataException("No schema found for type " + targetType.getName());
        }

        return stream(parallelism).map(map -> {
            validateRecord(map, schema);

            return (T) convertToType(map, schema, adapter);
        });
    }

    @SuppressWarnings("unchecked")
    private Object convertToType(Map<String, Object> map, SchemaModel schema, SchemaAdapter<?> adapter) {
        Object object = adapter.createTarget();
        for (int i = 0; i < schema.fields().size(); i++) {
            FieldModel fm = schema.fields().get(i);
            Object value = map.get(fm.getFieldName());
            if (fm.getFieldType() instanceof ObjectType && value instanceof Map) {
                ObjectType objectType = (ObjectType) fm.getFieldType();
                SchemaModel nestedSchema = config.namespaceT.findSchema(objectType.type());
                SchemaAdapter<?> nestedAdapter = config.adapterRegistry.resolve(nestedSchema.name());
                value = convertToType((Map<String, Object>) value, nestedSchema, nestedAdapter);
            }
            adapter.set(object, fm.getFieldName(), value);
        }
        return object;
    }

}
