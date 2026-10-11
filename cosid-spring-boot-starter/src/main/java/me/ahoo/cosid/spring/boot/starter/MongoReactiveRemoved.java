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

/**
 * MongoDB reactive support was removed in 4.0. Selecting the MongoDB backend with only the reactive driver on the
 * classpath fails at startup with this message instead of silently not creating the backend.
 *
 * @author ahoo wang
 * @since 4.0.0
 */
public final class MongoReactiveRemoved {
    public static final String MESSAGE = "CosId's MongoDB reactive support was removed in 4.0 and only the reactive MongoDB driver is on the "
        + "classpath. Add org.mongodb:mongodb-driver-sync: see docs/MIGRATION-4.0.md.";
    
    private MongoReactiveRemoved() {
    }
    
    /**
     * Fail the startup.
     *
     * @throws IllegalStateException always
     */
    public static <T> T fail() {
        throw new IllegalStateException(MESSAGE);
    }
}
