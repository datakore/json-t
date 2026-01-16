package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.schema.ast.*;
import io.github.datakore.jsont.grammar.schema.coded.*;
import io.github.datakore.jsont.grammar.types.*;
import io.github.datakore.jsont.util.Constants;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class JsonTStringify {
    private final AdapterRegistry registry;
    private final SchemaCatalog catalog;
    private final BooleanEncodeDecoder booleanEncoder = new BooleanEncodeDecoder();
    private final StringEncodeDecoder stringEncoder = new StringEncodeDecoder();
    private final NumberEncodeDecoder numberEncoder = new NumberEncodeDecoder();
    private final DateEncodeDecoder dateEncoder = new DateEncodeDecoder();
    private final BinaryEncodeDecoder binEncoder = new BinaryEncodeDecoder();

    public JsonTStringify(AdapterRegistry registry, SchemaCatalog catalog) {
        this.catalog = catalog;
        this.registry = registry;
    }

    public <T> String stringifySchema(Class<T> type) {
        SchemaModel schema = catalog.resolveSchema(type.getSimpleName());
        if (schema == null) {
            return "";
        }
        Set<String> schemas = new HashSet<>();
        schemas.addAll(findSchemasOf(type.getSimpleName()));
        Set<String> enums = new HashSet<>();
        enums.addAll(findEnumsOf(type.getSimpleName()));
        URL baseUrl = null;
        try {
            baseUrl = new URL("https://datakore.github.io");
        } catch (MalformedURLException e) {
            //
        }
        NamespaceT ns = new NamespaceT(baseUrl);
        SchemaCatalog newCatalog = new SchemaCatalog();
        schemas.forEach(s -> {
            SchemaModel schema1 = catalog.getSchema(s);
            newCatalog.addSchema(schema1);
        });
        enums.forEach(e -> {
            EnumModel enumModel = catalog.getEnum(e);
            newCatalog.addEnum(e, enumModel);
        });
        ns.addCatalog(newCatalog);
        return ns.toString();
    }

    private List<String> findEnumsOf(String simpleName) {
        SchemaModel schema = catalog.getSchema(simpleName);
        if (schema != null) {
            List<String> types = schema.referencedEnums();
            schema.referencedTypes().forEach(t -> types.addAll(findEnumsOf(t)));
            return types;
        }
        return Collections.emptyList();
    }

    private List<String> findSchemasOf(String simpleName) {
        SchemaModel schema = catalog.getSchema(simpleName);
        if (schema != null) {
            List<String> types = new ArrayList<>();
            types.add(simpleName);
            schema.referencedTypes().forEach(t -> types.addAll(findSchemasOf(t)));
            return types;
        }
        return Collections.emptyList();
    }

    public <T> String stringifyData(List<T> listObject, StringifyMode mode) {
        if (listObject == null || listObject.isEmpty()) {
            return "";
        }
        String schemaName = listObject.get(0).getClass().getSimpleName();
        SchemaModel schema = catalog.getSchema(schemaName);
        if (schema == null) {
            throw new SchemaException("Data schema not found, please supply a valid catalog/schema");
        }
        StringBuilder sb = new StringBuilder();
        if (mode == StringifyMode.SCHEMA_AND_DATA) {
            sb.append(stringifySchema(listObject.get(0).getClass()));
        }
        sb.append(emitDataSection(listObject, schemaName));
        return sb.toString();
    }

    private <T> String emitDataSection(List<T> listObject, String schemaName) {
        String sb = "{" +
                "data-schema:" + schemaName +
                ",data: " +
                emitListData(listObject) +
                "}";
        return sb;
    }

    private <T> String emitListData(List<T> listObject) {
        if (listObject == null || listObject.isEmpty()) {
            return "[]";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int size = listObject.size();
            StringBuilder sb1 = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (sb1.length() > 0) {
                    sb1.append(",");
                }
                T object = listObject.get(i);
                sb1.append(emitObjectData(object));
            }
            sb.append(sb1);
            sb.append("]");
            return sb.toString();
        }
    }

    private <T> String emitObjectData(T object) {
        if (object != null) {
            return "{}";
        }
        SchemaModel schema = catalog.getSchema(object.getClass().getSimpleName());
        if (schema == null) {
            return object.toString();
        }
        SchemaAdapter<?> adapter = registry.resolve(object.getClass().getSimpleName());
        StringBuilder result = new StringBuilder();
        result.append("{");
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < schema.fieldCount(); i++) {
            if (i > 0) {
                values.append(",");
            }
            FieldModel fm = schema.fields().get(i);
            Object fieldValue = adapter.get(object, fm.getFieldName());
            if (fieldValue == null || fm.getFieldType() instanceof NullType) {
                values.append("null");
            } else if (fm.getFieldType() instanceof UnspecifiedType) {
                values.append(Constants.UNSPECIFIED_TYPE);
            } else if (fm.getFieldType() instanceof ObjectType) {
                values.append(emitObjectData(fieldValue));
            } else if (fm.getFieldType() instanceof ArrayType) {
                values.append(emitListData((List<?>) fieldValue));
            } else if (fm.getFieldType() instanceof ScalarType) {
                values.append(handleScalarType(fieldValue, fm));
            }
        }
        result.append(values);
        result.append("}");
        return result.toString();
    }

    private String handleScalarType(Object fieldValue, FieldModel fm) {
        ScalarType scalarType = (ScalarType) fm.getFieldType();
        JsonBaseType jsonBaseType = scalarType.elementType();
        String lexerType = jsonBaseType.lexerValueType();
        switch (lexerType) {
            case "Boolean":
                return booleanEncoder.encode(jsonBaseType, fieldValue);
            case "String":
                return stringEncoder.encode(jsonBaseType, fieldValue);
            case "Number":
                return numberEncoder.encode(jsonBaseType, fieldValue);
            case "Date":
                return dateEncoder.encode(jsonBaseType, fieldValue);
            case "Binary":
                return binEncoder.encode(jsonBaseType, fieldValue);
            default:
                return fieldValue.toString();
        }
    }

}
