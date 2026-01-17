package io.github.datakore.jsont;

import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.stringify.StringifyMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonTTest {
    //String schemaPath = "src/test/resources/schema.jsont";
    String schemaPath = "src/test/resources/ns-schema.jsont";
    // String dataPath = "src/test/resources/data.jsont";
    String dataPath = "500000-1767884033236.jsont";
    String errorFile = "errors%d.json";


    Path scPath = Paths.get(schemaPath);
    Path datPath = Paths.get(dataPath);

    Path errorPath = Paths.get(String.format(errorFile, System.currentTimeMillis()));

    int total = 500000;
    ErrorCollector errorCollector = new DefaultErrorCollector();
    UserAdapter adapter1 = new UserAdapter();
    AddressAdapter adapter2 = new AddressAdapter();

    @Test
    void shouldProduceSchemaStringify() throws IOException {
        JsonTConfig config = JsonT.configureBuilder()
                .withAdapters(adapter1).withAdapters(adapter2)
                .withErrorCollector(errorCollector).withErrorFile(errorPath).source(scPath).build();
        System.out.println(config.stringify(User.class));
    }

    @Test
    void shouldStringifyData() throws IOException {
        Address add = new Address("Chennai", "12345", "ACTIVE");
        User u1 = new User(123, "sasikp", "ADMIN", add);
        u1.setEmail("sasikp@abcdef.com");
        u1.setTags(new String[]{"programmer"});
        JsonTConfig config = JsonT.configureBuilder()
                .withAdapters(adapter1).withAdapters(adapter2)
                .withErrorCollector(errorCollector).withErrorFile(errorPath).source(scPath).build();
        System.out.println(config.stringify(u1));
    }

    @Test
    void shouldGenerateJsonTString() throws IOException {

    }
}
