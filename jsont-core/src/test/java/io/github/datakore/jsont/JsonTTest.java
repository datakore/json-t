package io.github.datakore.jsont;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonTTest {
    //String schemaPath = "src/test/resources/schema.jsont";
    String schemaPath = "src/test/resources/schema.jsont";
    // String dataPath = "src/test/resources/data.jsont";
    String dataPath = "500000-1767884033236.jsont";


    Path scPath = Paths.get(schemaPath);
    Path datPath = Paths.get(dataPath);

    int total = 500000;

    @Test
    void shouldReadDataAsList() throws IOException {
        
    }

    @Test
    void shouldReadDataAsStream() throws IOException {

    }

    @Test
    void shouldGenerateJsonTString() throws IOException {

    }
}
