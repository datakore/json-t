package io.github.datakore.jsont.grammar.schema.ast;

import java.util.Arrays;
import java.util.List;

public enum JsonBaseType {
    //Signed Integers
    K_I16("i16"), K_I32("i32"), K_I64("i64"),
    //Unsigned Integers
    K_U16("u16"), K_U32("u32"), K_U64("u64"),
    // Signed Decimals
    K_D32("d32"), K_D64("d64"), K_D128("d128"),
    // Number based date, time, datetime, timestamp
    K_DATE("date"), K_TIME("time"), K_DATETIME("dtm"), K_TIMESTAMP("ts"),
    // String based date, time, datetime, timestamp with zone
    K_TSZ("tsz"), K_INST("inst"), K_INSTZ("insz"),
    // Integer year, month, day
    K_YEAR("yr"), K_MON("mon"), K_DAY("day"),
    // String based year+mon, month+day
    K_YEARMON("ym"), K_MNDAY("md"),
    // Base64, ObjectID, Hex
    K_BIN("b64"), K_OID("oid"), K_HEX("hex"),
    // Raw string, normalized string, URI, UUID
    K_STRING("str"), K_NSTR("nstr"), K_URI("uri"), K_UUID("uuid"),
    // Boolean
    K_BOOLEAN("bool");

    private final String identifier;
    private final List<String> numberTypes;
    private final List<String> stringTypes;
    private final List<String> booleanTypes;
    private final List<String> binaryTypes;
    private final List<String> dateTypes;
    private final String[] NUMBER_TYPES = {"i16", "i32", "i64", "u16", "u32", "u64", "d32", "d64", "d128"};
    private final String[] DATE_TYPES = {"date", "time", "dtm", "ts", "tsz", "inst", "insz", "yr", "mon", "day", "ym", "md"};
    private final String[] BINARY_TYPES = {"b64", "oid", "hex"};
    private final String[] STRING_TYPES = {"str", "nstr", "uri", "uuid"};
    private final String[] BOOLEAN_TYPES = {"bool"};

    JsonBaseType(String identifier) {
        this.numberTypes = Arrays.asList(NUMBER_TYPES);
        this.stringTypes = Arrays.asList(STRING_TYPES);
        this.booleanTypes = Arrays.asList(BOOLEAN_TYPES);
        this.binaryTypes = Arrays.asList(BINARY_TYPES);
        this.dateTypes = Arrays.asList(DATE_TYPES);

        this.identifier = identifier;
    }

    public static JsonBaseType byIdentifier(String type) {
        for (JsonBaseType baseType : JsonBaseType.values()) {
            if (baseType.identifier.equalsIgnoreCase(type)) {
                return baseType;
            }
        }
        return null;
    }

    public static String baseTypeNameByIdentifier(String type) {
        return byIdentifier(type).identifier;
    }

    public String identifier() {
        return identifier;
    }

    public String lexerValueType() {
        if (stringTypes.contains(identifier)) {
            return "String";
        } else if (numberTypes.contains(identifier)) {
            return "Number";
        } else if (booleanTypes.contains(identifier)) {
            return "Boolean";
        } else if (binaryTypes.contains(identifier)) {
            return "Binary";
        } else if (dateTypes.contains(identifier)) {
            return "Date";
        } else {
            return "Unknown";
        }
    }
}
