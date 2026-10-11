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

package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * cosid-jdbc is an optional dependency of the starter, but it is on the test classpath. Load the configuration
 * properties in a class loader without it, the way Redis / ZooKeeper / Mongo / Proxy users run the starter.
 */
class WithoutJdbcClasspathTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "me.ahoo.cosid.spring.boot.starter.machine.MachineProperties",
        "me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties"
    })
    void propertiesShouldNotNeedCosIdJdbc(String className) throws Exception {
        try (URLClassLoader classLoader = classLoaderWithoutCosIdJdbc()) {
            assertThatThrownBy(() -> classLoader.loadClass("me.ahoo.cosid.jdbc.JdbcSupport"))
                .isInstanceOf(ClassNotFoundException.class);

            Object properties = Class.forName(className, true, classLoader).getDeclaredConstructor().newInstance();

            assertThat(properties).isNotNull();
        }
    }

    private static URLClassLoader classLoaderWithoutCosIdJdbc() throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (isCosIdJdbc(entry)) {
                continue;
            }
            urls.add(new File(entry).toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    private static boolean isCosIdJdbc(String classpathEntry) {
        String normalized = classpathEntry.replace(File.separatorChar, '/');
        return normalized.contains("/cosid-jdbc/") || normalized.matches(".*/cosid-jdbc-[^/]*\\.jar");
    }
}
