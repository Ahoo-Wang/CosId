/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosid.provider;

import me.ahoo.cosid.IdGenerator;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author ahoo wang
 */
public class DefaultIdGeneratorProvider implements IdGeneratorProvider {

    private IdGenerator shareIdGenerator;

    private final ConcurrentHashMap<String, IdGenerator> nameMapIdGen;

    public DefaultIdGeneratorProvider() {
        this.nameMapIdGen = new ConcurrentHashMap<>();
    }

    @Override
    public IdGenerator getShare() {
        return shareIdGenerator;
    }

    @Override
    public void setShare(IdGenerator idGenerator) {
        this.shareIdGenerator = idGenerator;
        set(SHARE, idGenerator);
    }

    @Override
    public Optional<IdGenerator> get(String name) {
        IdGenerator idGen = nameMapIdGen.get(name);
        return Optional.ofNullable(idGen);
    }

    @Override
    public void set(String name, IdGenerator idGenerator) {
        nameMapIdGen.put(name, idGenerator);
    }

    @Override
    public IdGenerator getOrCreate(String name, Supplier<IdGenerator> idGenSupplier) {
        return nameMapIdGen.computeIfAbsent(name, (__) -> idGenSupplier.get());
    }

    @Override
    public Collection<IdGenerator> getAll() {
        return nameMapIdGen.values();
    }

}
