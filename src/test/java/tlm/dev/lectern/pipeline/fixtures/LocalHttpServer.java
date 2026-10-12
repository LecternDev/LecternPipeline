package tlm.dev.lectern.pipeline.fixtures;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class LocalHttpServer implements AutoCloseable {

    private static final String PATH = "/manifest";

    private final HttpServer server;

    public LocalHttpServer(int status, String body) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        server.createContext(PATH, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
            exchange.close();
        });
        server.start();
    }

    public URI manifestUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + PATH);
    }

    public URI unknownUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/other");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
