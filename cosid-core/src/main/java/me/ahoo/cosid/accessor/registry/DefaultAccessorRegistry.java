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

package me.ahoo.cosid.accessor.registry;

import me.ahoo.cosid.accessor.CosIdAccessor;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default CosIdAccessorRegistry implementation.
 *
 * @author ahoo wang
 */
public class DefaultAccessorRegistry implements CosIdAccessorRegistry {

    private final ConcurrentHashMap<Class<?>, CosIdAccessor> classMapAccessor = new ConcurrentHashMap<>();
    private final CosIdAccessorParser accessorParser;

    public DefaultAccessorRegistry(CosIdAccessorParser accessorParser) {
        this.accessorParser = accessorParser;
    }

    @Override
    public void register(Class<?> clazz) {
        register(clazz, accessorParser.parse(clazz));
    }

    @Override
    public void register(Class<?> clazz, CosIdAccessor cosIdAccessor) {
        classMapAccessor.put(clazz, cosIdAccessor);
    }

    @Override
    public CosIdAccessor get(Class<?> clazz) {
        return classMapAccessor.computeIfAbsent(clazz, accessorParser::parse);
    }
}
