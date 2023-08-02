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

import com.google.common.base.Strings;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link IdGenerator} container.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdGeneratorProvider {
    
    /**
     * the key of shared ID generator.
     */
    String SHARE = "__share__";
    
    /**
     * Get shared ID generator.
     *
     * @return shared ID generator
     */
    IdGenerator getShare();
    
    /**
     * Set shared ID generator.
     *
     * @param idGenerator Id Generator
     */
    void setShare(IdGenerator idGenerator);
    
    /**
     * Remove shared ID generator.
     *
     * @return Previous ID generator
     */
    IdGenerator removeShare();
    
    /**
     * Get ID generator by name.
     *
     * @param name name of ID generator
     * @return ID generator
     */
    Optional<IdGenerator> get(String name);
    
    default IdGenerator getRequired(String name) {
        return get(name)
            .orElseThrow(() -> new IllegalArgumentException(Strings.lenientFormat("Not found ID generator named:[%s]!", name)));
    }
    
    /**
     * Set ID generator by name.
     *
     * @param name name of ID generator
     * @param idGenerator ID generator
     */
    void set(String name, IdGenerator idGenerator);
    
    /**
     * remove ID generator by name.
     *
     * @param name name of ID generator
     * @return Previous ID generator
     */
    IdGenerator remove(String name);
    
    /**
     * clear all ID generator.
     */
    void clear();
    
    Set<Map.Entry<String, IdGenerator>> entries();
    
    /**
     * get all ID generator.
     *
     * @return all ID generator
     */
    Collection<IdGenerator> getAll();
    
}
