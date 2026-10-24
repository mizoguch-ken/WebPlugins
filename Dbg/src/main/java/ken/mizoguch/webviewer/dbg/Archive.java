package ken.mizoguch.webviewer.dbg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * the trees the jar of the plug-in ships with : the files of the debugger of the page and the
 * files of the panel of the plug-in are kept in the jar, so the page needs no network.<br>
 * The plug-in writes them once in a directory created for the run of the WebViewer and removes
 * them when the plug-in closes, so an other user of the computer can not read the files of the
 * page.<br>
 * The jar is read with the path of its own file when it has one ; the directory of the build is
 * read when the classes of the plug-in are not in a jar yet, as during the tests of the build.
 *
 * @author mizoguch-ken
 */
final class Archive {

    /**
     * the trees of the jar of the plug-in written in the directory of the run
     *
     * @param owner a class of the plug-in, the jar of the class holds the trees
     * @param local the directory created for the run
     * @param report receiver of the name of an entry which is refused, may be null
     * @param trees names of the trees to write, firebug-lite and dbg for example
     * @return the number of the files written
     * @throws java.io.IOException when the jar of the plug-in can not be read
     */
    static int write(Class<?> owner, Path local, Consumer<String> report, String... trees) throws IOException {
        Path source = location(owner);

        if (source == null) {
            return writeStream(owner, local, report, trees);
        }
        if (Files.isDirectory(source)) {
            return writeDirectory(source, local, report, trees);
        }
        return writeJar(source, local, report, trees);
    }

    /**
     * the file or the directory which holds the classes of the plug-in, null when it can not be
     * read as a file
     *
     * @param owner a class of the plug-in
     * @return the path of the jar or of the directory of the classes, null when it is unknown
     */
    private static Path location(Class<?> owner) {
        try {
            URL url = owner.getProtectionDomain().getCodeSource().getLocation();

            if ((url == null) || !"file".equalsIgnoreCase(url.getProtocol())) {
                return null;
            }
            return Paths.get(url.toURI());
        } catch (Exception ex) {
            // an other kind of source is read with the stream of the code source of the class
            return null;
        }
    }

    private static int writeJar(Path jar, Path local, Consumer<String> report, String... trees) throws IOException {
        int written = 0;

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path file = target(local, entry.getName(), report, trees);

                if (file == null) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                    continue;
                }
                Files.createDirectories(file.getParent());
                try (InputStream in = jarFile.getInputStream(entry)) {
                    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                }
                written++;
            }
        }
        return written;
    }

    private static int writeStream(Class<?> owner, Path local, Consumer<String> report, String... trees)
            throws IOException {
        int written = 0;

        try (ZipInputStream zip = new ZipInputStream(
                owner.getProtectionDomain().getCodeSource().getLocation().openStream())) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                Path file = target(local, entry.getName(), report, trees);

                if (file == null) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                    continue;
                }
                Files.createDirectories(file.getParent());
                Files.copy(zip, file, StandardCopyOption.REPLACE_EXISTING);
                written++;
            }
        }
        return written;
    }

    private static int writeDirectory(Path source, Path local, Consumer<String> report, String... trees)
            throws IOException {
        final int[] written = new int[1];

        try (Stream<Path> tree = Files.walk(source)) {
            tree.forEach(file -> {
                String name = source.relativize(file).toString().replace(File.separatorChar, '/');
                Path to = target(local, name, report, trees);

                if (to == null) {
                    return;
                }
                try {
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(to);
                        return;
                    }
                    Files.createDirectories(to.getParent());
                    Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
                    written[0]++;
                } catch (IOException ex) {
                    tell(report, name);
                }
            });
        }
        return written[0];
    }

    /**
     * the file of the directory of the run which holds an entry of the jar : an entry of an other
     * archive must not write outside the directory of the plug-in
     *
     * @param local the directory created for the run
     * @param name name of the entry of the jar
     * @param report receiver of the name of a refused entry, may be null
     * @param trees names of the trees kept
     * @return the file to write, null when the entry is not kept
     */
    private static Path target(Path local, String name, Consumer<String> report, String... trees) {
        if (!wanted(name, trees)) {
            return null;
        }
        Path file = local.resolve(name).normalize();

        if (!file.startsWith(local)) {
            tell(report, name);
            return null;
        }
        return file;
    }

    /**
     * an entry of the jar belongs to one of the trees the plug-in keeps
     *
     * @param name name of the entry
     * @param trees names of the trees
     * @return true when the entry is written in the directory of the run
     */
    private static boolean wanted(String name, String... trees) {
        if (name == null) {
            return false;
        }
        for (String tree : trees) {
            if (name.equals(tree) || name.startsWith(tree + "/")) {
                return true;
            }
        }
        return false;
    }

    private static void tell(Consumer<String> report, String name) {
        if (report != null) {
            report.accept(name);
        }
    }

    private Archive() {
    }
}
