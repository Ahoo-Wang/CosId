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

package me.ahoo.cosid.runtime;

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.tck.InMemoryStore;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A store provider found by {@link java.util.ServiceLoader} (META-INF/services in the test resources).
 */
public final class ServiceLoadedStores implements StoreProvider {
    static final String TYPE = "service-loaded";
    static final AtomicInteger OPENED = new AtomicInteger();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Store open(StoreConfig config) {
        OPENED.incrementAndGet();
        return new InMemoryStore();
    }
}
