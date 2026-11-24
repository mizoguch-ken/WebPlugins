package ken.mizoguch.webviewer.llm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;

/**
 * Shared sandboxed path resolution, bounded UTF-8 file reading (TOCTOU-safe), and atomic
 * temporary-file UTF-8 persistence helpers.
 *
 * @author mizoguch-ken
 */
final class SandboxFiles {

    private SandboxFiles() {
    }

    static Path resolve(Path directory, Path page, String path) {
        return SandboxPath.resolve(directory, page, path);
    }

    static String readUtf8Bounded(Path file, long maxBytes, String label) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException(LlmI18n.get("llm.sandbox.notRegularFile", label, file.getFileName()));
        }
        long announcedSize = Files.size(file);
        if (announcedSize > maxBytes) {
            throw new IOException(LlmI18n.get("llm.sandbox.exceedsMaxBytes", label, maxBytes));
        }
        int initialCap = (int) Math.min(Math.max(announcedSize, 256L), 65536L);
        ByteArrayOutputStream out = new ByteArrayOutputStream(initialCap);
        byte[] buf = new byte[8192];
        long total = 0L;
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException(LlmI18n.get("llm.sandbox.exceedsMaxBytes", label, maxBytes));
                }
                out.write(buf, 0, n);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    static void writeUtf8Atomically(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException(LlmI18n.get("llm.sandbox.noParentDir", file));
        }
        Files.createDirectories(parent);
        String prefix = (file.getFileName() == null) ? "tmp" : file.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "tmp_" + prefix;
        }
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }
}
