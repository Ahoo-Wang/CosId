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

package me.ahoo.cosid.migration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@code plan.json} and {@code apply-result.json}: indented JSON of the records of this package.
 */
public final class Json {
    static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private Json() {
    }

    /**
     * Writes a value to a file.
     *
     * @param file  the file
     * @param value the value
     */
    public static void write(Path file, Object value) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, MAPPER.writeValueAsString(value) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Writing " + file + " failed", e);
        }
    }

    /**
     * Reads a value from a file.
     *
     * @param file the file
     * @param type the type
     * @param <T>  the type
     * @return the value
     */
    public static <T> T read(Path file, Class<T> type) {
        try {
            return MAPPER.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * The SHA-256 of a file, in hex: how {@code apply-result.json} names its plan.
     *
     * @param file the file
     * @return the digest
     */
    public static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file + " failed", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
