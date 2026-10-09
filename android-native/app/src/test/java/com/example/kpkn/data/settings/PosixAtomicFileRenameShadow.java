package com.example.kpkn.data.settings;

import android.util.AtomicFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Test-only POSIX rename for Android's API31+ AtomicFile implementation.
 * Windows File.renameTo cannot replace an existing target, unlike Android's
 * rename syscall. All registry decoding, validation and fail-closed behavior
 * remain real; the native contract separately exercises Android AtomicFile.
 */
@Implements(value = AtomicFile.class, minSdk = 31)
public class PosixAtomicFileRenameShadow {
    @Implementation(minSdk = 31)
    protected static void rename(File source, File target) throws IOException {
        if (target.isDirectory() && !target.delete()) {
            throw new IOException("Cannot remove directory at AtomicFile target");
        }
        Files.move(source.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
