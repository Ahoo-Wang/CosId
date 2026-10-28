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

package me.ahoo.cosid.store.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;

/**
 * The replica set the tests share: {@code COSID_TEST_MONGODB_URI} when set (the 4.x convention), otherwise a
 * single-node replica set in Testcontainers, on {@code COSID_TEST_MONGODB_IMAGE} ({@value #DEFAULT_IMAGE} by
 * default).
 */
final class MongoTestSupport {
    static final String DEFAULT_IMAGE = "mongo:7.0";
    static final String DATABASE = "cosid_test";
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static String uri;
    private static MongoClient client;

    private MongoTestSupport() {
    }

    static synchronized String uri() {
        if (uri == null) {
            uri = start();
        }
        return uri;
    }

    private static String start() {
        String external = System.getenv("COSID_TEST_MONGODB_URI");
        if (external != null && !external.isBlank()) {
            return external;
        }
        MongoDBContainer container = container();
        start(container);
        // Stopped by the Testcontainers reaper when the test JVM exits.
        return container.getReplicaSetUrl(DATABASE);
    }

    /** A single-node replica set on {@code COSID_TEST_MONGODB_IMAGE}, not started yet; start it with {@link #start}. */
    static MongoDBContainer container() {
        String image = System.getenv("COSID_TEST_MONGODB_IMAGE");
        return new MongoDBContainer(image == null || image.isBlank() ? DEFAULT_IMAGE : image).withReplicaSet();
    }

    /**
     * Starts the container; when it does not come up, the failure carries the output of mongod, which a container that
     * exits during startup takes with it (CI uploads the test results of a failed job).
     */
    static void start(MongoDBContainer container) {
        ToStringConsumer output = new ToStringConsumer();
        container.withLogConsumer(output);
        try {
            container.start();
        } catch (ContainerLaunchException e) {
            throw new IllegalStateException("MongoDB container " + container.getDockerImageName() + " did not start; its output:\n"
                + output.toUtf8String(), e);
        }
    }

    static synchronized MongoClient client() {
        if (client == null) {
            client = MongoClients.create(uri());
        }
        return client;
    }

    static MongoDatabase database() {
        return client().getDatabase(DATABASE);
    }
}
