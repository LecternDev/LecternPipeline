package tlm.dev.lectern.pipeline.fixtures;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalFileServer implements AutoCloseable {

    private static final long STALL_MILLIS = 6_000;

    private final HttpServer server;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    public LocalFileServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.start();
    }

    public LocalFileServer serve(String path, int status, byte[] body) {
        hits.put(path, new AtomicInteger());
        server.createContext(path, exchange -> {
            hits.get(path).incrementAndGet();
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        return this;
    }

    public LocalFileServer serveThenStall(String path, byte[] firstBytes) {
        hits.put(path, new AtomicInteger());
        server.createContext(path, exchange -> {
            hits.get(path).incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(firstBytes);
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(STALL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        return this;
    }

    public URI uri(String path) {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path);
    }

    public int hits(String path) {
        return hits.get(path).get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
