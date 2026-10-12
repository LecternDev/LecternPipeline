package tlm.dev.lectern.pipeline.fetch;

import java.nio.file.Path;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class ServerJarService {

    private static final String VERSION_JSON = "version JSON";
    private static final String SERVER_JAR = "server jar";
    private static final long VERSION_JSON_LIMIT = 8L * 1024 * 1024;
    private static final long SIZE_SLACK = 1024L * 1024;

    private final DownloadSource source;
    private final JarStore store;
    private final VersionJsonReader reader;
    private final Sha1Hasher hasher;

    public ServerJarService(DownloadSource source, JarStore store, VersionJsonReader reader, Sha1Hasher hasher) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    public FetchedJar ensure(VersionJsonRef ref) {
        ServerDownload server = readServerDownload(ref);
        Path jar = store.jarPath(ref.id());
        if (store.hasJar(ref.id())) {
            checkExistingJar(ref.id(), jar, server);
            return new FetchedJar(ref.id(), jar, server, false);
        }
        download(ref.id(), server);
        return new FetchedJar(ref.id(), jar, server, true);
    }

    private ServerDownload readServerDownload(VersionJsonRef ref) {
        byte[] versionJson;
        try {
            versionJson = source.readBytes(ref.url(), VERSION_JSON_LIMIT);
        } catch (DownloadException exception) {
            throw withVersion(ref.id(), exception);
        }
        Sha1 received = hasher.hash(versionJson);
        if (!received.equals(ref.sha1())) {
            throw IntegrityException.hash(ref.id(), VERSION_JSON, ref.sha1(), received);
        }
        return reader.readServerDownload(ref.id(), versionJson);
    }

    private void checkExistingJar(VersionId id, Path jar, ServerDownload server) {
        long size = store.sizeOf(jar);
        if (size != server.size()) {
            throw IntegrityException.existingJar(id, jar, "Expected " + server.size() + " bytes, found " + size + " bytes");
        }
        Sha1 found = hasher.hash(jar);
        if (!found.equals(server.sha1())) {
            throw IntegrityException.existingJar(id, jar, "Expected SHA-1 " + server.sha1().hex() + ", found " + found.hex());
        }
    }

    private void download(VersionId id, ServerDownload server) {
        Path part = store.newPartFile(id);
        try {
            source.saveTo(server.url(), part, server.size() + SIZE_SLACK);
            verifyDownloaded(id, part, server);
            store.publish(part, id);
        } catch (DownloadException exception) {
            discardAfter(part, exception);
            throw withVersion(id, exception);
        } catch (RuntimeException exception) {
            discardAfter(part, exception);
            throw exception;
        }
    }

    private void verifyDownloaded(VersionId id, Path part, ServerDownload server) {
        long size = store.sizeOf(part);
        if (size != server.size()) {
            throw IntegrityException.size(id, SERVER_JAR, server.size(), size);
        }
        Sha1 received = hasher.hash(part);
        if (!received.equals(server.sha1())) {
            throw IntegrityException.hash(id, SERVER_JAR, server.sha1(), received);
        }
    }

    private void discardAfter(Path part, RuntimeException failure) {
        try {
            store.discard(part);
        } catch (RuntimeException discardFailure) {
            failure.addSuppressed(discardFailure);
        }
    }

    private static DownloadException withVersion(VersionId id, DownloadException exception) {
        return new DownloadException("Version " + id.value() + ": " + exception.getMessage(), exception);
    }
}
