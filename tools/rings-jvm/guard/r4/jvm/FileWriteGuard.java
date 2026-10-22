package r4.jvm;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Permission;

/** Java 17 agent: production/worktrees stay read-only, including on assertion failure. */
@SuppressWarnings("removal")
public final class FileWriteGuard extends SecurityManager {
    private final Path root;
    private final java.util.List<Path> allowed = new java.util.ArrayList<>();
    /** allowedRoots: la raíz del arnés y, separadas por '|', carpetas extra donde sí se puede escribir. */
    private FileWriteGuard(String allowedRoots) throws IOException {
        final String[] parts = allowedRoots.split("\\|");
        root = new File(parts[0]).getCanonicalFile().toPath();
        for (String part : parts) allowed.add(new File(part).getCanonicalFile().toPath());
    }
    public static void premain(String allowedRoot, Instrumentation ignored) throws IOException {
        System.setSecurityManager(new FileWriteGuard(allowedRoot));
    }
    @Override public void checkPermission(Permission permission) {
        if (permission instanceof RuntimePermission && permission.getName().equals("setSecurityManager")) {
            throw new SecurityException("r4-jvm: el guardián de escritura no se puede desactivar");
        }
    }
    @Override public void checkPermission(Permission permission, Object context) { checkPermission(permission); }
    @Override public void checkWrite(String file) { checkPath(file); }
    @Override public void checkDelete(String file) { checkPath(file); }
    private void checkPath(String file) {
        final Path target;
        try { target = new File(file).getCanonicalFile().toPath(); }
        catch (IOException failure) { throw new SecurityException("r4-jvm: ruta no resoluble: " + file, failure); }
        for (Path path : allowed) if (target.startsWith(path)) return;
        try {
            Files.writeString(root.resolve("build/blocked-writes.log"), target + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new SecurityException("r4-jvm: no se pudo registrar la escritura bloqueada", failure);
        }
        throw new SecurityException("r4-jvm: escritura fuera del arnés bloqueada: " + target);
    }
}
