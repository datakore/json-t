package io.github.datakore.jsont.tests;

import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.datagen.UserGenerator;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.util.ProgressMonitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ParseToConvertTests extends BaseTests {

    Path generateUserData(int recordCount, boolean includeSchema) throws IOException {
        String schemaPath = "src/test/resources/ns-schema.jsont";
        UserGenerator userGenerator = new UserGenerator();
        userGenerator.initialize();
        StreamingJsonTWriter<User> stringifier = getTypedStreamWriter(schemaPath, User.class, userGenerator, loadUserAdapters());
        Writer writer;
        Path temp = null;
        String path = String.format("target/user-data%d.jsont", System.currentTimeMillis());
        temp = Files.createFile(Paths.get(path));
        writer = Files.newBufferedWriter(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        int batchSize = 10;
        int flushSize = 5;
        ProgressMonitor monitor = new ProgressMonitor(recordCount, batchSize, flushSize);
        monitor.startProgress();
        stringifier.stringify(writer, recordCount, batchSize, flushSize, includeSchema, monitor);
        monitor.endProgress();
        return temp.toAbsolutePath();
    }

    @Test
    void shouldParseUserData() throws IOException {
        jsonTConfig = JsonT.configureBuilder().withAdapters(loadUserAdapters()).withErrorCollector(errorCollector).build();
        try {
            Path path = generateUserData(1, true);
            jsonTConfig.source(path).parse(4)
                    .doOnNext(
                            rowNode -> System.out.println(rowNode.values())
                    ).blockLast();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
