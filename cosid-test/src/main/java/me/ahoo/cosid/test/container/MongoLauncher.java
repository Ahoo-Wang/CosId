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

package me.ahoo.cosid.test.container;


import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

public class MongoLauncher {
    public static final String CONNECTION_STRING_PROPERTY = "cosid.test.mongodb.uri";
    public static final String CONNECTION_STRING_ENV = "COSID_TEST_MONGODB_URI";
    private static final String CONNECTION_OPTIONS = "connectTimeoutMS=300000&maxIdleTimeMS=300000";
    private static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer(DockerImageName.parse("mongo:6.0.12"));

    private MongoLauncher() {
    }

    public static String getConnectionString() {
        Optional<String> explicitConnectionString = resolveExplicitConnectionString(
            System.getProperty(CONNECTION_STRING_PROPERTY),
            System.getenv(CONNECTION_STRING_ENV)
        );
        if (explicitConnectionString.isPresent()) {
            return explicitConnectionString.get();
        }

        MONGO_CONTAINER.start();
        return appendConnectionOptions(MONGO_CONTAINER.getConnectionString());
    }

    static Optional<String> resolveExplicitConnectionString(String propertyValue, String environmentValue) {
        Optional<String> propertyConnectionString = trimToOptional(propertyValue);
        if (propertyConnectionString.isPresent()) {
            return propertyConnectionString;
        }
        return trimToOptional(environmentValue);
    }

    private static Optional<String> trimToOptional(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmedValue);
    }

    private static String appendConnectionOptions(String connectionString) {
        String delimiter = connectionString.contains("?") ? "&" : "?";
        return connectionString + delimiter + CONNECTION_OPTIONS;
    }
}
