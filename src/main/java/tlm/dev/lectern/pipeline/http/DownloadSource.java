package tlm.dev.lectern.pipeline.http;

import java.net.URI;
import java.nio.file.Path;

public interface DownloadSource {

    byte[] readBytes(URI url, long maxBytes);

    void saveTo(URI url, Path target, long maxBytes);
}
