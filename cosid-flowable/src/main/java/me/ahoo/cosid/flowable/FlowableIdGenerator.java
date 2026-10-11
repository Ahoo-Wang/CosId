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

package me.ahoo.cosid.flowable;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.provider.LazyIdGenerator;

/**
 * Flowable IdGenerator Based on CosId.
 */
public class FlowableIdGenerator implements org.flowable.common.engine.impl.cfg.IdGenerator {
    /**
     * The key of the system property that can be used to set the id generator name.
     */
    public static final String ID_KEY = "cosid.flowable";
    private final LazyIdGenerator idGenerator;

    /**
     * Creates a generator that resolves from {@link DefaultIdGeneratorProvider#INSTANCE}.
     */
    public FlowableIdGenerator() {
        this(DefaultIdGeneratorProvider.INSTANCE);
    }

    /**
     * Creates a generator that resolves the generator named by the {@link #ID_KEY} system property
     * (default {@link IdGeneratorProvider#SHARE}) from the given provider.
     *
     * @param idGeneratorProvider the provider to resolve the generator from
     */
    public FlowableIdGenerator(IdGeneratorProvider idGeneratorProvider) {
        this(System.getProperty(ID_KEY, IdGeneratorProvider.SHARE), idGeneratorProvider);
    }

    /**
     * Creates a generator that resolves the named generator from the given provider.
     *
     * @param generatorName       the generator name
     * @param idGeneratorProvider the provider to resolve the generator from
     */
    public FlowableIdGenerator(String generatorName, IdGeneratorProvider idGeneratorProvider) {
        this.idGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
    }
    
    public String getNextId() {
        return idGenerator.generateAsString();
    }
}
