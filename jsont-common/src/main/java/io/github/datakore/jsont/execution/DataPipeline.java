package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class DataPipeline implements DataStream {
    private final Sinks.Many<Map<String, Object>> sink;
    private final int backPressureSize;
    private final Duration waitingTimeWhenQueueIsFull;
    private final CSVErrorLogger errorLogger;

    public DataPipeline(int backPressureSize, Duration waitingTimeWhenQueueIsFull, CSVErrorLogger errorLogger) {
        this.backPressureSize = backPressureSize;
        this.waitingTimeWhenQueueIsFull = waitingTimeWhenQueueIsFull;
        this.errorLogger = errorLogger;

        this.sink = Sinks.many()
                .unicast()
                .onBackpressureBuffer(
                        Queues.<Map<String, Object>>get(this.backPressureSize).get()
                );
    }

    @Override
    public void onRowParsed(Map<String, Object> row) {
        long start = System.nanoTime();
        long timeoutNanos = waitingTimeWhenQueueIsFull.toNanos();

        while (true) {
            // Attempt to emit
            Sinks.EmitResult result = sink.tryEmitNext(row);

            if (result.isSuccess()) {
                return; // Success! Move to next row.
            }

            // Check Timeout
            if (System.nanoTime() - start > timeoutNanos) {
                String msg = "Producer timed out waiting for consumer. Pipeline full for " + timeoutNanos + "ns";
                RuntimeException exception = new RuntimeException(msg);
                sink.tryEmitError(exception);
                throw exception; // Abort the parser thread
            }

            if (result == Sinks.EmitResult.FAIL_OVERFLOW || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                // Buffer is full. Wait a tiny bit (backoff) and retry.  This effectively "BLOCKS" the parser thread.
                LockSupport.parkNanos(100_000); // 0.1ms wait
            } else {
                // Fatal error (e.g., stream cancelled)
                throw new RuntimeException("Emission failed: " + result);
            }
        }
    }

    @Override
    public void onEOF() {
        sink.tryEmitComplete();
    }

    @Override
    public Flux<Map<String, Object>> rows() {
        return sink.asFlux();
    }


    @Override
    public void onRowError(int rowIndex, List<ValidationError> errors) {
        errorLogger.log(rowIndex, errors);

        if (errors.stream().anyMatch(err -> err.severity() == Severity.FATAL)) {
            sink.tryEmitError(new RuntimeException("Fatal error processing row " + rowIndex));
        }
        // Continue further processing
    }
}
