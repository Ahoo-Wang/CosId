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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

class WindowFileTest {
    @TempDir
    Path dir;

    @Test
    void readsWhatItWrote() throws IOException {
        WindowFile file = new WindowFile(dir.resolve("nested/slot.window"));
        assertThat(file.read(), is(OptionalLong.empty()));
        file.write(1_234);
        assertThat(file.read(), is(OptionalLong.of(1_234)));
        file.write(5_678);
        assertThat(file.read(), is(OptionalLong.of(5_678)));
        assertThat(Files.exists(dir.resolve("nested/slot.window.tmp")), is(false));
        assertThat(file.path(), is(dir.resolve("nested/slot.window").toAbsolutePath()));
    }

    @Test
    void overwritesATemporaryFileLeftByACrash() throws IOException {
        Path path = dir.resolve("slot.window");
        Files.writeString(dir.resolve("slot.window.tmp"), "garbage");
        WindowFile file = new WindowFile(path);
        file.write(42);
        assertThat(file.read(), is(OptionalLong.of(42)));
    }

    @Test
    void refusesAFileItCannotRead() throws IOException {
        Path path = dir.resolve("slot.window");
        Files.writeString(path, "ceiling=soon\n");
        assertThrows(CosIdException.class, () -> new WindowFile(path).read());
        Files.writeString(path, "something else\n");
        assertThrows(CosIdException.class, () -> new WindowFile(path).read());
        assertThrows(UncheckedIOException.class, () -> new WindowFile(dir).read());
    }

    @Test
    void failsToWriteBelowAFile() throws IOException {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "");
        assertThrows(UncheckedIOException.class, () -> new WindowFile(blocker.resolve("slot.window")).write(1));
    }
}
