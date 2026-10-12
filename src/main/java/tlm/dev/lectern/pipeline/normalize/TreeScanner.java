package tlm.dev.lectern.pipeline.normalize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class TreeScanner {

    private static final String LINK_REASON = "it is a symbolic link, a junction or a special file";

    public List<String> scan(VersionId id, Path root, DataAllowlist.Layout layout) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new UnexpectedFileException(id, root.getFileName().toString(), "it is not a plain folder");
        }
        List<String> kept = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw new UnexpectedFileException(id, relativeName(directory), LINK_REASON);
                    }
                    boolean topLevelCache = layout == DataAllowlist.Layout.GENERATED
                            && root.equals(directory.getParent())
                            && directory.getFileName().toString().equals(DataAllowlist.CACHE_FOLDER);
                    return topLevelCache ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String relative = relativeName(file);
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw new UnexpectedFileException(id, relative, LINK_REASON);
                    }
                    switch (DataAllowlist.check(layout, relative)) {
                        case DataAllowlist.Verdict.Keep _ -> kept.add(relative);
                        case DataAllowlist.Verdict.Ignore _ -> {
                        }
                        case DataAllowlist.Verdict.Reject reject -> throw new UnexpectedFileException(id, relative, reject.reason());
                    }
                    return FileVisitResult.CONTINUE;
                }

                private String relativeName(Path path) {
                    Path relative = root.relativize(path);
                    String name = relative.toString().replace('\\', '/');
                    return name.isEmpty() ? root.getFileName().toString() : name;
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not walk " + root, exception);
        }
        return List.copyOf(kept.stream().sorted().toList());
    }
}
