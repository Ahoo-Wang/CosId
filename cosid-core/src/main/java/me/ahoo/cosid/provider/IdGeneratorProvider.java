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
import me.ahoo.cosid.internal.Checks;

import com.google.errorprone.annotations.ThreadSafe;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Container for managing named {@link StringIdGenerator} instances.
 *
 * <p>A provider holds both {@link IdGenerator}s ({@code long} ids) and generators of string ids only, such as
 * {@link me.ahoo.cosid.cosid.CosIdGenerator}. Use {@link #get(String, Class)} or {@link #getRequired(String, Class)}
 * to look one up by the type you need, for example {@code getRequired(name, IdGenerator.class)} to call
 * {@code generate()}. The shared generator is always an {@link IdGenerator}.
 * 
 * <p>This interface provides a registry for ID generators that can be accessed
 * by name throughout an application. It supports both named generators and a
 * special shared generator that can be used as a default.
 * 
 * <p>The provider acts as a centralized repository for ID generators, enabling:
 * <ul>
 *   <li>Dependency injection of named generators</li>
 *   <li>Centralized configuration and management</li>
 *   <li>Easy switching between different generator implementations</li>
 *   <li>Sharing of a common generator across multiple components</li>
 * </ul>
 * 
 * <p>Common use cases include:
 * <ul>
 *   <li>Managing different ID generators for different entity types</li>
 *   <li>Providing a default generator for components that don't specify one</li>
 *   <li>Enabling runtime configuration of ID generation strategies</li>
 * </ul>
 * 
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdGeneratorProvider {
    
    /**
     * The key used for the shared (default) ID generator.
     * 
     * <p>This constant defines the name used to access the shared ID generator,
     * which serves as a default generator for components that don't require
     * a specific named generator.
     */
    String SHARE = "__share__";
    
    /**
     * Get the shared (default) ID generator.
     * 
     * <p>This method returns the shared ID generator that can be used as a
     * default when no specific named generator is required.
     *
     * @return The shared ID generator
     */
    IdGenerator getShare();
    
    /**
     * Set the shared (default) ID generator.
     * 
     * <p>This method updates the shared ID generator that will be returned
     * by {@link #getShare()}.
     *
     * @param idGenerator The ID generator to set as shared
     */
    void setShare(IdGenerator idGenerator);
    
    /**
     * Remove the shared (default) ID generator.
     * 
     * <p>This method removes the current shared ID generator and returns it,
     * leaving no shared generator configured.
     *
     * @return The previous shared ID generator, or null if none was set
     */
    IdGenerator removeShare();
    
    /**
     * Get an ID generator by its name.
     * 
     * <p>This method returns an optional containing the ID generator with the
     * specified name, or an empty optional if no generator with that name exists.
     *
     * @param name The name of the ID generator to retrieve
     * @return An optional containing the ID generator, or empty if not found
     */
    Optional<StringIdGenerator> get(String name);

    /**
     * Get an ID generator by its name and type.
     *
     * @param name The name of the ID generator to retrieve
     * @param type The type the generator must have, such as {@code IdGenerator.class}
     * @param <T> The generator type
     * @return An optional containing the ID generator, or empty if not found
     * @throws IllegalArgumentException if the generator with that name is not of the given type
     */
    default <T extends StringIdGenerator> Optional<T> get(String name, Class<T> type) {
        return get(name).map(idGenerator -> {
            if (!type.isInstance(idGenerator)) {
                throw new IllegalArgumentException(Checks.lenientFormat("ID generator named:[%s] is a [%s], not a [%s]!",
                    name, idGenerator.getClass().getName(), type.getName()));
            }
            return type.cast(idGenerator);
        });
    }
    
    /**
     * Get an ID generator by its name, throwing an exception if not found.
     * 
     * <p>This method returns the ID generator with the specified name, or throws
     * an {@link IllegalArgumentException} if no generator with that name exists.
     *
     * @param name The name of the ID generator to retrieve
     * @return The ID generator with the specified name
     * @throws IllegalArgumentException if no generator with the specified name exists
     */
    default StringIdGenerator getRequired(String name) {
        return getRequired(name, StringIdGenerator.class);
    }

    /**
     * Get an ID generator by its name and type, throwing an exception if not found.
     *
     * @param name The name of the ID generator to retrieve
     * @param type The type the generator must have, such as {@code IdGenerator.class}
     * @param <T> The generator type
     * @return The ID generator with the specified name
     * @throws IllegalArgumentException if no generator with the specified name exists, or it is not of the given type
     */
    default <T extends StringIdGenerator> T getRequired(String name, Class<T> type) {
        return get(name, type)
            .orElseThrow(() -> new IllegalArgumentException(Checks.lenientFormat("Not found ID generator named:[%s]!", name)));
    }
    
    /**
     * Set an ID generator with the specified name.
     * 
     * <p>This method registers an ID generator with the specified name, replacing
     * any existing generator with the same name.
     *
     * @param name The name to register the generator under
     * @param idGenerator The ID generator to register
     * @throws IllegalArgumentException if the name is {@link #SHARE} and the generator is not an {@link IdGenerator}
     */
    void set(String name, StringIdGenerator idGenerator);
    
    /**
     * Remove an ID generator by its name.
     * 
     * <p>This method removes the ID generator with the specified name and returns
     * the removed generator, or null if no generator with that name existed.
     *
     * @param name The name of the ID generator to remove
     * @return The removed ID generator, or null if not found
     */
    StringIdGenerator remove(String name);
    
    /**
     * Clear all registered ID generators.
     * 
     * <p>This method removes all registered ID generators, including the shared
     * generator, leaving the provider empty.
     */
    void clear();
    
    /**
     * Get all registered ID generator entries.
     * 
     * <p>This method returns a set of map entries representing all registered
     * ID generators, including their names and instances.
     *
     * @return A set of entries for all registered ID generators
     */
    Set<Map.Entry<String, StringIdGenerator>> entries();
    
    /**
     * Get all registered ID generators.
     * 
     * <p>This method returns a collection of all registered ID generator instances,
     * without their associated names.
     *
     * @return A collection of all registered ID generators
     */
    Collection<StringIdGenerator> getAll();
    
}
