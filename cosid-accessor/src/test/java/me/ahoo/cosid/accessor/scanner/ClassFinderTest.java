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

package me.ahoo.cosid.accessor.scanner;

import me.ahoo.cosid.accessor.scanner.entity.OrderEntity;
import me.ahoo.cosid.accessor.scanner.entity.OrderItemEntity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class ClassFinderTest {

    @Test
    void findInDirectory() throws IOException {
        Set<String> classNames = ClassFinder.findTopLevelClasses(getClass().getClassLoader(), "me.ahoo.cosid.accessor.scanner");

        Assertions.assertTrue(classNames.contains(ClassFinderTest.class.getName()));
        Assertions.assertTrue(classNames.contains(OrderEntity.class.getName()));
        Assertions.assertTrue(classNames.contains(OrderItemEntity.class.getName()));
        Assertions.assertTrue(classNames.stream().noneMatch(name -> name.contains("$")));
    }

    @Test
    void findInJar() throws IOException {
        Set<String> classNames = ClassFinder.findTopLevelClasses(getClass().getClassLoader(), "org.junit.jupiter.api");

        Assertions.assertTrue(classNames.contains(Assertions.class.getName()));
        Assertions.assertTrue(classNames.contains("org.junit.jupiter.api.function.Executable"));
        Assertions.assertFalse(classNames.contains("org.junit.jupiter.api.package-info"));
    }

    @Test
    void findMissingPackage() throws IOException {
        Assertions.assertTrue(ClassFinder.findTopLevelClasses(getClass().getClassLoader(), "me.ahoo.cosid.missing").isEmpty());
    }

    @Test
    void findInJarWithoutDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path jar = tempDir.resolve("no-directory-entries.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entry : new String[] {"com/example/nodir/Order.class", "com/example/nodir/Order$Line.class", "com/example/nodir/item/Item.class"}) {
                out.putNextEntry(new JarEntry(entry));
                out.write(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
                out.closeEntry();
            }
        }

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            Assertions.assertFalse(classLoader.getResources("com/example/nodir").hasMoreElements());
            Set<String> classNames = ClassFinder.findTopLevelClasses(classLoader, "com.example.nodir");

            Assertions.assertEquals(Set.of("com.example.nodir.Order", "com.example.nodir.item.Item"), classNames);
        }
    }
}
