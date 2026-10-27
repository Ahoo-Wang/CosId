/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.internal;

import me.ahoo.cosid.CosIdException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * The window file of a {@code StaticSlot} (05-machine-lease.md §4.4): one ceiling, written to a temporary file,
 * fsynced, then moved over the old file atomically, so a crash leaves either the old or the new ceiling, never a torn
 * one.
 */
public final class WindowFile {
    private static final String KEY = "ceiling=";
    private final Path path;
    private final Path temporary;

    /**
     * A window file.
     *
     * @param path the file
     */
    public WindowFile(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath();
        this.temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
    }

    public Path path() {
        return path;
    }

    /**
     * Reads the persisted ceiling.
     *
     * @return the ceiling in milliseconds, or empty if the file does not exist
     * @throws CosIdException       if the file is not a window file: refusing beats guessing a floor
     * @throws UncheckedIOException if the file cannot be read
     */
    public OptionalLong read() {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return OptionalLong.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the window file " + path, e);
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(KEY)) {
                try {
                    return OptionalLong.of(Long.parseLong(trimmed.substring(KEY.length()).trim()));
                } catch (NumberFormatException e) {
                    throw new CosIdException("Malformed ceiling in the window file " + path + ": " + trimmed, e);
                }
            }
        }
        throw new CosIdException("No ceiling in the window file " + path);
    }

    /**
     * Persists {@code ceiling} durably: when this returns, a crash cannot lose it.
     *
     * @param ceiling the ceiling in milliseconds
     * @throws UncheckedIOException if the file cannot be written
     */
    public void write(long ceiling) {
        byte[] bytes = ("# CosId StaticSlot window: the next start issues timestamps above this ceiling (ms).\n" + KEY + ceiling + "\n")
            .getBytes(StandardCharsets.UTF_8);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("The file system of " + path + " cannot replace files atomically", e);
            }
            if (parent != null) {
                syncDirectory(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the window file " + path, e);
        }
    }

    /** Makes the rename durable; not every platform can open a directory (Windows cannot), and there it is skipped. */
    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            // Best effort: the data itself is already fsynced.
        }
    }
}
