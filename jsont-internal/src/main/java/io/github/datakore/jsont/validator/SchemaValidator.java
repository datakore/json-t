package io.github.datakore.jsont.validator;

import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

import java.util.Map;

public class SchemaValidator {

    private final NamespaceT namespace;
    private final ErrorCollector errorCollector;

    public SchemaValidator(NamespaceT namespace, ErrorCollector errorCollector) {
        this.namespace = namespace;
        this.errorCollector = errorCollector;
    }

    public void validate(SchemaModel schema, Map<String, Object> map) {

    }
}
