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

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ClassUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs once that the deprecated reactive MongoDB support is in use.
 *
 * <p>The reactive beans are only used when the synchronous driver is absent: with both drivers, the synchronous
 * beans are {@code @Primary}.</p>
 *
 * @author ahoo wang
 */
@Slf4j
public final class MongoReactiveDeprecation {
    private static final String SYNC_MONGO_CLIENT = "com.mongodb.client.MongoClient";
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);
    
    private MongoReactiveDeprecation() {
    }
    
    public static void warnIfSelected() {
        warnIfSelected(ClassUtils.isPresent(SYNC_MONGO_CLIENT, MongoReactiveDeprecation.class.getClassLoader()), WARNED);
    }
    
    /**
     * @return whether the warning was logged by this call
     */
    static boolean warnIfSelected(boolean syncDriverPresent, AtomicBoolean warned) {
        if (syncDriverPresent || !warned.compareAndSet(false, true)) {
            return false;
        }
        if (log.isWarnEnabled()) {
            log.warn("CosId reactive MongoDB support (me.ahoo.cosid.mongo.reactive) is deprecated and will be removed in 4.0: "
                + "it only blocks on the reactive driver. Add org.mongodb:mongodb-driver-sync to use the synchronous support instead.");
        }
        return true;
    }
}
