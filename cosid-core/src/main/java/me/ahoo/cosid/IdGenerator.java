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

package me.ahoo.cosid;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.stat.Statistical;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nonnull;

/**
 * Id Generator.
 *
 * <p>This is the core interface for generating distributed IDs in the CosId library.
 * It provides two primary methods for ID generation:
 * <ul>
 *   <li>{@link #generate()} - Generates a numeric ID as a long value</li>
 *   <li>{@link #generateAsString()} - Generates a string representation of the ID</li>
 * </ul>
 *
 * <p>The interface extends {@link StringIdGenerator} to provide string ID generation
 * capabilities and {@link Statistical} to provide statistical information about
 * the generator's state.
 *
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdGenerator extends StringIdGenerator, Statistical {

    /**
     * Get the ID converter used to convert {@code long} type IDs to {@link String}.
     *
     * <p>By default, this returns {@link Radix62IdConverter#PAD_START} which converts
     * long IDs to radix-62 string representations with padding to ensure consistent
     * string lengths.
     *
     * @return ID converter for transforming numeric IDs to string format
     */
    @Nonnull
    default IdConverter idConverter() {
        return Radix62IdConverter.PAD_START;
    }

    /**
     * Generate a distributed ID as a long value.
     *
     * <p>This method generates a unique numeric identifier that is guaranteed to be
     * unique within the distributed system. The exact algorithm used depends on
     * the implementation (e.g., Snowflake, Segment, CosId).
     *
     * @return A unique distributed ID as a long value
     */
    long generate();

    /**
     * Generate a distributed ID as a string value.
     *
     * <p>This method generates a unique string identifier by first generating a
     * numeric ID via {@link #generate()} and then converting it to a string
     * using the configured {@link #idConverter()}.
     *
     * @return A unique distributed ID as a string value
     */
    @Nonnull
    @Override
    default String generateAsString() {
        return idConverter().asString(generate());
    }

    /**
     * Get statistical information about this ID generator.
     *
     * <p>This method provides insights into the generator's current state, including
     * implementation details and converter statistics. This is useful for monitoring
     * and debugging purposes.
     *
     * @return Statistical information about this ID generator
     */
    @Override
    default IdGeneratorStat stat() {
        return IdGeneratorStat.simple(getClass().getSimpleName(), idConverter().stat());
    }
}
