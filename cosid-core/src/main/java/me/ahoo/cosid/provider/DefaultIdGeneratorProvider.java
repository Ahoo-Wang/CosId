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

package me.ahoo.cosid.provider;

import me.ahoo.cosid.IdGenerator;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class DefaultIdGeneratorProvider implements IdGeneratorProvider {

    public static final IdGeneratorProvider INSTANCE = new DefaultIdGeneratorProvider();
    private volatile IdGenerator shareIdGenerator;

    private final ConcurrentHashMap<String, IdGenerator> nameMapIdGen;

    public DefaultIdGeneratorProvider() {
        nameMapIdGen = new ConcurrentHashMap<>();
    }

    @Override
    public IdGenerator getShare() {
        return shareIdGenerator;
    }

    @Override
    public void setShare(IdGenerator idGenerator) {
        shareIdGenerator = idGenerator;
        set(SHARE, idGenerator);
    }

    @Override
    public IdGenerator removeShare() {
        shareIdGenerator = null;
        return nameMapIdGen.remove(SHARE);
    }

    @Override
    public Optional<IdGenerator> get(String name) {
        IdGenerator idGen = nameMapIdGen.get(name);
        return Optional.ofNullable(idGen);
    }

    @Override
    public IdGenerator remove(String name) {
        if (SHARE.equals(name)) {
            return removeShare();
        }
        return nameMapIdGen.remove(name);
    }

    @Override
    public void set(String name, IdGenerator idGenerator) {
        nameMapIdGen.put(name, idGenerator);
    }

    @Override
    public void clear() {
        shareIdGenerator = null;
        nameMapIdGen.clear();
    }

    @Override
    public Collection<IdGenerator> getAll() {
        return nameMapIdGen.values();
    }

}
