package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.ValidationError;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface DataStream {
    void onRowParsed(Map<String, Object> row);

    void onEOF();

    Flux<Map<String, Object>> rows();


    void onRowError(int rowIndex, List<ValidationError> errors);
}
