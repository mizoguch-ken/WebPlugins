/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.plugin.kit;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * path given by the javascript of the page : the page uses a path from the directory of the
 * plug-in, a path which starts with "~" is used from the directory of the page. A path which
 * leaves the directory it is taken from is refused, so the page can not reach the other files
 * of the computer.
 *
 * @author mizoguch-ken
 */
public final class SandboxPath {

    /**
     * path used from the directory of the plug-in
     *
     * @param directory directory of the plug-in
     * @param path path given by the page
     * @return the path to use, null when the path can not be used
     */
    public static Path resolve(Path directory, String path) {
        return resolve(directory, directory, path);
    }

    /**
     * path used by an operation of the page
     *
     * @param directory directory of the plug-in
     * @param page directory of the page, used by a path which starts with "~"
     * @param path path given by the page
     * @return the path to use, null when the path can not be used
     */
    public static Path resolve(Path directory, Path page, String path) {
        Path base;
        String name;

        if ((directory == null) || (path == null) || path.trim().isEmpty()) {
            return null;
        }
        path = path.trim();
        if (path.startsWith("~")) {
            base = (page != null) ? page : directory;
            name = path.substring(1);
            // the name of a path of the page is always used from the directory of the page :
            // a name which starts with a separator would be used from the root of the drive on
            // the platforms which read a name starting with a separator as an absolute name
            while (name.startsWith("/") || name.startsWith("\\")) {
                name = name.substring(1);
            }
        } else {
            base = directory;
            name = path;
        }
        base = base.toAbsolutePath().normalize();
        if (name.isEmpty()) {
            return base;
        }

        Path file;
        try {
            file = base.resolve(name).normalize();
        } catch (InvalidPathException ex) {
            // the name holds a character which the file system does not accept
            return null;
        }
        // the file has to stay in the directory it is taken from : a path which reaches an
        // other directory is refused
        if (!file.startsWith(base)) {
            return null;
        }
        return file;
    }

    /**
     * path used by an operation of the page, refused by an exception when it can not be used
     *
     * @param directory directory of the plug-in
     * @param page directory of the page, used by a path which starts with "~"
     * @param path path given by the page
     * @return the path to use
     * @throws IOException when the path can not be used
     */
    public static Path require(Path directory, Path page, String path) throws IOException {
        Path file = resolve(directory, page, path);

        if (file == null) {
            throw new IOException("Incorrect file path [" + path + "]");
        }
        return file;
    }

    private SandboxPath() {
    }
}
