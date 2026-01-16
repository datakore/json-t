package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.JsonTListener;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.parser.DataRowVisitor;
import io.github.datakore.jsont.parser.SchemaCatalogVisitor;
import org.antlr.v4.runtime.CharStream;

public class ParserExecutor {
    public static void executeSchema(CharStream stream, ErrorCollector collector, SchemaCatalogVisitor listener) {
        executeParse(stream, collector, listener);
    }

    public static void executeDataParse(CharStream stream, ErrorCollector collector, DataRowVisitor listener) {
        executeParse(stream, collector, listener);
    }

    private static void executeParse(CharStream stream, ErrorCollector collector, JsonTListener listener) {
        JsonTParser parser = ParserUtil.createParser(stream, collector, listener);
        parser.jsonT();
    }
}
