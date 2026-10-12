package tlm.dev.lectern.pipeline.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

final class TransferWatchdog implements AutoCloseable {

    private final AtomicBoolean expired = new AtomicBoolean();
    private final Thread thread;

    TransferWatchdog(InputStream body, Duration timeout) {
        this.thread = Thread.ofVirtual().name("transfer-watchdog").start(() -> {
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException cancelled) {
                return;
            }
            expired.set(true);
            try {
                body.close();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    boolean expired() {
        return expired.get();
    }

    @Override
    public void close() {
        thread.interrupt();
    }
}
