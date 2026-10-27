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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The 4.x writer of the tests (09 §5): {@code Legacy4xDriver} of the {@code legacy4x} source set, loaded with the
 * released 4.1.0 jars in a class loader whose parent is the platform loader, so 4.x {@code me.ahoo.cosid} classes never
 * meet the 5.0 ones of this test class path.
 */
final class Legacy4xWriter implements AutoCloseable {
    private static final ClassLoader LOADER = loader();
    private final BiFunction<String, Map<String, Object>, Object> driver;
    private final Map<String, Object> backend;

    @SuppressWarnings("unchecked")
    Legacy4xWriter(String backend, String url) {
        try {
            this.driver = (BiFunction<String, Map<String, Object>, Object>) LOADER.loadClass("me.ahoo.cosid.migration.legacy.Legacy4xDriver")
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        this.backend = Map.of("backend", backend, "url", url);
    }

    private static ClassLoader loader() {
        String classpath = System.getProperty("cosid.migration.legacy4x");
        if (classpath == null) {
            throw new IllegalStateException("-Dcosid.migration.legacy4x is not set: run the tests through Gradle");
        }
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            try {
                urls.add(new File(entry).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
        return new URLClassLoader("cosid-4.x", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private Object call(String operation, Object... pairs) {
        Map<String, Object> args = new HashMap<>(backend);
        for (int i = 0; i < pairs.length; i += 2) {
            args.put((String) pairs[i], pairs[i + 1]);
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(LOADER);
        try {
            return driver.apply(operation, args);
        } finally {
            Thread.currentThread().setContextClassLoader(context);
        }
    }

    /** Fetches {@code fetches} segments of {@code step}; returns the last max id. */
    long segment(String namespace, String name, long offset, long step, long fetches) {
        return (Long) call("segment", "namespace", namespace, "name", name, "offset", offset, "step", step, "fetches", fetches);
    }

    /** Fetches through a 4.x grouped distributor by day ({@code yyyyMMdd}): {groupKey, maxId}. */
    @SuppressWarnings("unchecked")
    Map<String, Object> grouped(String namespace, String name, long step, long fetches) {
        return (Map<String, Object>) call("grouped", "namespace", namespace, "name", name, "step", step, "fetches", fetches);
    }

    /** Distributes machine ids and generates snowflake ids on them: {maxId, maxTimestamp, machineIds}. */
    @SuppressWarnings("unchecked")
    Map<String, Object> machines(String namespace, int instances, long ids) {
        return (Map<String, Object>) call("machines", "namespace", namespace, "instances", instances, "ids", ids, "machineBit", 10);
    }

    /** Starts a 4.x instance nobody stopped. */
    String startLeftover(String namespace, String name, long step, long intervalMillis) {
        return (String) call("leftover.start", "namespace", namespace, "name", name, "step", step, "intervalMillis", intervalMillis);
    }

    /** Stops it: every max id it fetched. */
    @SuppressWarnings("unchecked")
    List<Long> stopLeftover(String handle) {
        return (List<Long>) call("leftover.stop", "handle", handle);
    }

    @Override
    public void close() {
        call("close");
    }
}
