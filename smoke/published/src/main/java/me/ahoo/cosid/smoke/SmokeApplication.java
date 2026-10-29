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

package me.ahoo.cosid.smoke;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Issues ids through the published starter and stores (R2-build-01/02) and exits: 0 when they increase, otherwise
 * the failure.
 */
@SpringBootApplication
public class SmokeApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SmokeApplication.class, args);
        System.exit(SpringApplication.exit(context));
    }

    @Bean
    ApplicationRunner smoke(CosIdRuntime runtime, IdGenerator snowflake) {
        return arguments -> {
            System.out.println("Spring Boot " + SpringBootVersion.getVersion() + ", Lettuce " + version(io.lettuce.core.RedisClient.class)
                + ", MongoDB driver " + version(com.mongodb.client.MongoClient.class));
            increasing("default (snowflake, slot leased from Redis)", snowflake);
            increasing("order (segment on MongoDB)", runtime.generator("order"));
            System.out.println("slot " + runtime.slot().orElseThrow());
        };
    }

    private static void increasing(String name, IdGenerator ids) {
        long last = Long.MIN_VALUE;
        for (int i = 0; i < 1000; i++) {
            long id = ids.generate();
            if (id <= last) {
                throw new IllegalStateException(name + ": " + id + " after " + last);
            }
            last = id;
        }
        System.out.println(name + ": 1000 increasing ids, the last " + last);
    }

    private static String version(Class<?> type) {
        String version = type.getPackage().getImplementationVersion();
        return version == null ? "(" + type.getProtectionDomain().getCodeSource().getLocation() + ")" : version;
    }
}
