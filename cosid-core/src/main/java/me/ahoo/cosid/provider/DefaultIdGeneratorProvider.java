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
import me.ahoo.cosid.StringIdGenerator;

import com.google.errorprone.annotations.ThreadSafe;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link IdGeneratorProvider} implementation.
 *
 * <p>Thread-safe registry for managing named ID generator instances
 * using a {@link ConcurrentHashMap} for concurrent access.
 *
 * @author ahoo wang
 */
@ThreadSafe
public class DefaultIdGeneratorProvider implements IdGeneratorProvider {

    /**
     * Shared singleton instance.
     */
    public static final IdGeneratorProvider INSTANCE = new DefaultIdGeneratorProvider();
    private volatile IdGenerator shareIdGenerator;

    private final ConcurrentHashMap<String, StringIdGenerator> nameMapIdGen;

    /**
     * Creates a new instance with an empty registry.
     */
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
        nameMapIdGen.put(SHARE, idGenerator);
    }

    @Override
    public IdGenerator removeShare() {
        IdGenerator removed = shareIdGenerator;
        shareIdGenerator = null;
        nameMapIdGen.remove(SHARE);
        return removed;
    }

    @Override
    public Optional<StringIdGenerator> get(String name) {
        StringIdGenerator idGen = nameMapIdGen.get(name);
        return Optional.ofNullable(idGen);
    }

    @Override
    public StringIdGenerator remove(String name) {
        if (SHARE.equals(name)) {
            return removeShare();
        }
        return nameMapIdGen.remove(name);
    }

    @Override
    public void set(String name, StringIdGenerator idGenerator) {
        if (SHARE.equals(name)) {
            if (!(idGenerator instanceof IdGenerator share)) {
                throw new IllegalArgumentException("The shared ID generator must be an IdGenerator, not a [" + idGenerator.getClass().getName() + "]!");
            }
            setShare(share);
            return;
        }
        nameMapIdGen.put(name, idGenerator);
    }

    @Override
    public void clear() {
        shareIdGenerator = null;
        nameMapIdGen.clear();
    }

    @Override
    public Set<Map.Entry<String, StringIdGenerator>> entries() {
        return nameMapIdGen.entrySet();
    }

    @Override
    public Collection<StringIdGenerator> getAll() {
        return nameMapIdGen.values();
    }

}
