package tlm.dev.lectern.pipeline.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;

public final class HttpDownloadClient implements DownloadSource {

    private static final int HTTP_OK = 200;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final HttpClient http;
    private final Duration responseTimeout;
    private final Duration transferTimeout;

    public HttpDownloadClient(HttpClient http, Duration responseTimeout, Duration transferTimeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
        this.transferTimeout = Objects.requireNonNull(transferTimeout, "transferTimeout");
    }

    @Override
    public byte[] readBytes(URI url, long maxBytes) {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        copyBody(url, collected, maxBytes);
        return collected.toByteArray();
    }

    @Override
    public void saveTo(URI url, Path target, long maxBytes) {
        try (OutputStream file = Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            copyBody(url, file, maxBytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write the download of " + url + " to " + target, exception);
        }
    }

    private void copyBody(URI url, OutputStream to, long maxBytes) {
        InputStream body = open(url);
        TransferWatchdog watchdog = new TransferWatchdog(body, transferTimeout);
        try (body; watchdog) {
            copyLimited(body, to, maxBytes, url);
        } catch (IOException exception) {
            throw watchdog.expired() ? timedOut(url) : failure(url, exception);
        }
        if (watchdog.expired()) {
            throw timedOut(url);
        }
    }

    private InputStream open(URI url) {
        HttpRequest request = HttpRequest.newBuilder(url).timeout(responseTimeout).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException exception) {
            throw failure(url, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransferException("Interrupted while downloading " + url, exception);
        }
        if (response.statusCode() != HTTP_OK) {
            TransferException failure = new TransferException("The download of " + url + " answered HTTP " + response.statusCode());
            try {
                response.body().close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return response.body();
    }

    private static void copyLimited(InputStream from, OutputStream to, long maxBytes, URI url) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        for (int read = from.read(buffer); read >= 0; read = from.read(buffer)) {
            total += read;
            if (total > maxBytes) {
                throw new TransferException("The download of " + url + " is larger than the " + maxBytes + " bytes allowed");
            }
            to.write(buffer, 0, read);
        }
    }

    private TransferException timedOut(URI url) {
        return new TransferException("The download of " + url + " did not finish within " + transferTimeout.toSeconds() + " seconds");
    }

    private static TransferException failure(URI url, IOException exception) {
        if (exception instanceof HttpTimeoutException) {
            return new TransferException("The server of " + url + " did not answer in time", exception);
        }
        if (exception instanceof InterruptedIOException) {
            Thread.currentThread().interrupt();
            return new TransferException("Interrupted while downloading " + url, exception);
        }
        return new TransferException("Could not download " + url + ": " + exception, exception);
    }
}
