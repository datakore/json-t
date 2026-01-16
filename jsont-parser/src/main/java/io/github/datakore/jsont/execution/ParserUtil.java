package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.JsonTLexer;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.listener.JsonTErrorListener;
import org.antlr.v4.runtime.ANTLRErrorStrategy;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;

public final class ParserUtil {

    public static JsonTParser createParser(
            CharStream input, ErrorCollector errorCollector,
            ParseTreeListener listener) {
        JsonTErrorListener errorListener = new JsonTErrorListener(errorCollector);
        JsonTLexer lexer = new JsonTLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JsonTParser parser = new JsonTParser(tokens);
        parser.setBuildParseTree(false);
        ANTLRErrorStrategy handler = new BailErrorStrategy();
        parser.setErrorHandler(handler);
        lexer.removeErrorListeners();
        parser.removeParseListeners();
        parser.removeErrorListeners();

        lexer.addErrorListener(errorListener);
        parser.addParseListener(listener);
        parser.addErrorListener(errorListener);
        return parser;
    }
}
