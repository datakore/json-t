package io.github.datakore.jsont.parser;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.execution.DataStream;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.schema.coded.BooleanEncodeDecoder;
import io.github.datakore.jsont.grammar.schema.coded.NumberEncodeDecoder;
import io.github.datakore.jsont.grammar.schema.coded.StringEncodeDecoder;
import io.github.datakore.jsont.grammar.types.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.datakore.jsont.util.Constants.UNSPECIFIED_TOKEN;

public final class DataRowVisitor extends SchemaCatalogVisitor {

    private final DataStream pipeline;
    private final AtomicInteger rowIndex = new AtomicInteger();
    private final NamespaceT givenNamespace;
    // The Stack handles Recursion and Nesting naturally
    private final Stack<ParsingContext> stack = new Stack<>();
    private final BooleanEncodeDecoder booleanDecoder = new BooleanEncodeDecoder();
    private final NumberEncodeDecoder numberDecoder = new NumberEncodeDecoder();
    private final StringEncodeDecoder stringDecoder = new StringEncodeDecoder();
    private SchemaModel dataSchema;


    public DataRowVisitor(ErrorCollector errorCollector, NamespaceT givenNamespace, DataStream stream) {
        super(errorCollector);
        this.givenNamespace = givenNamespace;
        this.pipeline = stream;
    }

    NamespaceT resolvedNamespace() {
        NamespaceT namespace = null;
        if (super.getNamespaceT() != null) {
            namespace = super.getNamespaceT();
        } else {
            namespace = givenNamespace;
        }
        if (namespace == null) {
            throw new SchemaException("Namespace cannot be null");
        }
        return namespace;
    }

    SchemaModel resolveSchema(String schemaName) {
        NamespaceT ns = resolvedNamespace();
        for (SchemaCatalog sc : ns.getCatalogs()) {
            SchemaModel model = sc.getSchema(schemaName);
            if (model != null) {
                return model;
            }
        }
        throw new ParseCancellationException("Unbale to resolve dataschema from Catalog supplied, cannot read data recordds");
    }

    @Override
    public void exitDataSchemaSection(JsonTParser.DataSchemaSectionContext ctx) {
        super.exitDataSchemaSection(ctx);
        String schema = ctx.SCHEMAID().getText();
        dataSchema = resolveSchema(schema);
    }

    @Override
    public void enterDataRow(JsonTParser.DataRowContext ctx) {
        super.clearErrors();
        rowIndex.getAndIncrement();
        stack.push(new StructContext(rowIndex.get(), dataSchema));
    }

    @Override
    public void exitDataRow(JsonTParser.DataRowContext ctx) {
        StructContext root = (StructContext) stack.pop();

        // Optional: Validate if all required fields were filled
        if (root.fieldIndex < root.schema.fields().size()) {
            super.addError(Severity.FATAL, "Not all fields have been shared", root.getLocation());
        }

        List<ValidationError> errors = super.getRowErrors();

        if (!errors.isEmpty()) {
            pipeline.onRowError(this.rowIndex.get(), errors);
        } else {
            pipeline.onRowParsed(root.values);
        }
    }

    @Override
    public void enterObjectValue(JsonTParser.ObjectValueContext ctx) {
        // Check if this is the top-level object (child of dataRow)
        if (ctx.getParent() instanceof JsonTParser.DataRowContext) {
            // We are entering the root object. The context is already on the stack from enterDataRow.
            // We don't need to do anything here.
            return;
        }

        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof ObjectType)) {
            super.addError(Severity.FATAL, "Found Object '{...}' but expected " + expected, stack.peek().getLocation());
        }

        assert expected instanceof ObjectType;
        ObjectType type = (ObjectType) expected;
        SchemaModel nestedSchema = resolveSchema(type.type()); // Recursive resolution
        stack.push(new StructContext(this.rowIndex.get(), expected.colPosition(), expected.fieldName(), nestedSchema));
    }

    @Override
    public void exitObjectValue(JsonTParser.ObjectValueContext ctx) {
        // Check if this is the top-level object (child of dataRow)
        if (ctx.getParent() instanceof JsonTParser.DataRowContext) {
            // We are exiting the root object. The context should remain on the stack until exitDataRow pops it.
            // We don't need to do anything here.
            return;
        }

        // Finished parsing nested object
        StructContext completed = (StructContext) stack.pop();
        // Optional: Validate if all required fields were filled
        if (completed.fieldIndex < completed.schema.fields().size()) {
            super.addError(Severity.FATAL, "Not all fields have been sent", completed.getLocation());
        }

        // Add the completed List to the Parent (which is now top of stack)
        stack.peek().addValue(completed.values);
    }

    @Override
    public void enterArrayValue(JsonTParser.ArrayValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof ArrayType)) {
            super.addError(Severity.FATAL, "Found array '[...]' but expected " + expected, stack.peek().getLocation());
        }

        assert expected instanceof ArrayType;
        ArrayType type = (ArrayType) expected;
        // Push Array Context knowing the type of its children
        stack.push(new ArrayContext(this.rowIndex.get(), expected.colPosition(), expected.fieldName(), type.getElementType()));
    }

    @Override
    public void exitArrayValue(JsonTParser.ArrayValueContext ctx) {
        ArrayContext completed = (ArrayContext) stack.pop();
        stack.peek().addValue(completed.values);
    }

    @Override
    public void enterEnumValue(JsonTParser.EnumValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof EnumType)) {
            super.addError(Severity.FATAL, "Found enum but expected " + expected, stack.peek().getLocation());
        }
        // We do NOT push a context for Enum, as it is a leaf value (conceptually)
    }

    @Override
    public void exitEnumValue(JsonTParser.EnumValueContext ctx) {
        // Add value directly to parent
        stack.peek().addValue(ctx.CONSTID().getText());
    }

    @Override
    public void enterScalarValue(JsonTParser.ScalarValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof ScalarType)) {
            super.addError(Severity.FATAL, "Found scalar value but expected " + expected, stack.peek().getLocation());
        }
    }

    @Override
    public void exitScalarValue(JsonTParser.ScalarValueContext ctx) {
        ParsingContext parent = stack.peek();
        ScalarType type = (ScalarType) parent.getExpectedType();
        JsonBaseType jsonBaseType = type.elementType();

        try {
            Object value;
            if (ctx.NULL() != null) {
                value = null;
            } else if (ctx.UNSPECIFIED() != null) {
                value = UNSPECIFIED_TOKEN;
            } else if (ctx.BOOLEAN() != null) {
                value = booleanDecoder.decode(jsonBaseType, ctx.BOOLEAN().getText());
            } else if (ctx.NUMBER() != null) {
                value = numberDecoder.decode(jsonBaseType, ctx.NUMBER().getText());
            } else {
                value = stringDecoder.decode(jsonBaseType, ctx.STRING().getText());
            }
            parent.addValue(value);
        } catch (Exception e) {
            super.addError(Severity.ROW_FATAL, "Error parsing scalar value", parent.getLocation());
        }
    }

    interface ParsingContext {
        void addValue(Object value);

        ValueType getExpectedType();

        ErrorLocation getLocation();
    }

    private final class StructContext implements ParsingContext {
        final SchemaModel schema;
        final Map<String, Object> values = new LinkedHashMap<>();
        private final ErrorLocation errorLocation;
        int fieldIndex = 0;

        StructContext(int row, SchemaModel schema) {
            this.schema = schema;
            this.errorLocation = new ErrorLocation(row, schema.name());
        }

        StructContext(int row, int col, String field, SchemaModel schema) {
            this.schema = schema;
            this.errorLocation = new ErrorLocation(row, col, field, schema.name());
        }

        @Override
        public void addValue(Object value) {
            values.put(schema.fields().get(fieldIndex).getFieldName(), value);
            fieldIndex++; // Move to next field
        }

        @Override
        public ValueType getExpectedType() {
            if (fieldIndex >= schema.fields().size()) {
                throw new SchemaException("Too many fields for schema " + schema.name());
            }
            return schema.fields().get(fieldIndex).getFieldType();
        }

        @Override
        public ErrorLocation getLocation() {
            return this.errorLocation;
        }
    }

    private final class ArrayContext implements ParsingContext {

        final List<Object> values = new ArrayList<>();
        private final ValueType componentType;
        private final ErrorLocation errorLocation;

        ArrayContext(int row, int col, String field, ValueType componentType) {
            this.componentType = componentType;
            this.errorLocation = new ErrorLocation(row, col, field);
        }

        @Override
        public void addValue(Object value) {
            values.add(value);
        }

        @Override
        public ValueType getExpectedType() {
            return componentType;
        }

        @Override
        public ErrorLocation getLocation() {
            return errorLocation;
        }
    }
}
