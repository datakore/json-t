package io.github.datakore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.marketplace.StringifyUtil;
import io.github.datakore.marketplace.entity.Order;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public Main() throws IOException {
    }

    public static void main(String[] args) throws IOException, RunnerException {
//        Options opt = new OptionsBuilder()
//                .include(JsonTBenchmark.class.getSimpleName())
//                .forks(1)
//                .jvmArgs(
//                        "-Xms4G",
//                        "-Xmx4G",
//                        "-XX:+UseZGC",
//                        "-XX:+ZGenerational"
//                )
//                .build();
//
//        new Runner(opt).run();
        Main main = new Main();
        main.compareStringifySizes(1);
        main.compareStringifySizes(10);
//        main.compareStringifySizes(100);
//        main.compareStringifySizes(1_000);
//        main.compareStringifySizes(10_000);
//        main.compareStringifySizes(100_000);
//        main.compareStringifySizes(200_000);
//        main.compareStringifySizes(500_000);
//        main.compareStringifySizes(1_000_000);
    }
    StringifyUtil util = new StringifyUtil();
    StreamingJsonTWriter<Order> jsontStringifier = util.createStreamingWriter();
    Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(LocalDate.class, new LocalDateAdapter()).create();

    public void compareStringifySizes(int count) throws IOException {
        AtomicLong counter = new AtomicLong();
        String pathFormat = "jsont-benchmark/target/marketplace_data-%d.%s";
        try (Writer jsonw = Files.newBufferedWriter(Paths.get(String.format(pathFormat, count, "json")));
             Writer jsontw = Files.newBufferedWriter(Paths.get(String.format(pathFormat, count, "jsont")))) {
            List<Order> list = util.createObjectList(count);
            gson.toJson(list, jsonw);
            jsontStringifier.stringify(list, jsontw, true);
        }
    }

    private class LocalDateAdapter extends TypeAdapter<LocalDate> {
        private final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE; // e.g., "2023-10-27"

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(FORMATTER));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                return LocalDate.parse(in.nextString(), FORMATTER);
            }
        }
    }
}
