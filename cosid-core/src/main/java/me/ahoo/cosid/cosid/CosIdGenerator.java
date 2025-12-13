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

package me.ahoo.cosid.cosid;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.generator.CosIdGeneratorStat;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.jspecify.annotations.NonNull;

/**
 * CosId algorithm ID generator.
 * 
 * <p>This interface implements the CosId algorithm, a distributed ID generation
 * approach that combines timestamp, machine ID, and sequence information in a
 * flexible format. Unlike traditional algorithms like Snowflake, CosId uses a
 * state-based approach that can be serialized to and from string representations.
 * 
 * <p>Key features of the CosId algorithm:
 * <ul>
 *   <li>State-based design with explicit timestamp, machine ID, and sequence components</li>
 *   <li>Flexible string representation using {@link CosIdIdStateParser}</li>
 *   <li>Support for different radix encodings (62, 36, etc.)</li>
 *   <li>Human-readable and sortable ID formats</li>
 * </ul>
 * 
 * <p>The CosId algorithm works by:
 * <ol>
 *   <li>Maintaining state information about the last generated ID</li>
 *   <li>Generating new IDs as {@link CosIdState} objects containing all components</li>
 *   <li>Converting states to string format using a {@link CosIdIdStateParser}</li>
 * </ol>
 * 
 * <p><img src="../doc-files/CosIdGenerator.png" alt="CosIdGenerator"></p>
 *
 * @author ahoo wang
 */
public interface CosIdGenerator extends IdGenerator {
    /**
     * Get the machine ID assigned to this generator.
     * 
     * <p>This is the unique identifier for the machine or instance that is
     * generating IDs, which ensures global uniqueness across the distributed
     * system.
     *
     * @return The machine ID
     */
    int getMachineId();
    
    /**
     * Get the timestamp of the last ID that was generated.
     * 
     * <p>This is used for clock synchronization and to ensure proper ordering
     * of generated IDs. It can also help detect clock drift.
     *
     * @return The timestamp of the last generated ID
     */
    long getLastTimestamp();
    
    /**
     * Get the state parser used to convert between ID states and string representations.
     * 
     * <p>The state parser is responsible for serializing {@link CosIdState} objects
     * to string format and deserializing strings back to states. Different parsers
     * can be used to create different string formats.
     *
     * @return The state parser for this generator
     */
    @NonNull
    CosIdIdStateParser getStateParser();
    
    /**
     * Get the ID converter (unsupported in CosIdGenerator).
     * 
     * <p>CosIdGenerator does not support the traditional {@link IdConverter} approach
     * because it uses a state-based design with explicit {@link CosIdIdStateParser}
     * for string conversion. This method always throws {@link UnsupportedOperationException}.
     *
     * @return Never returns (always throws exception)
     * @throws UnsupportedOperationException always thrown because CosIdGenerator uses state parsers instead
     */
    @Override
    default @NonNull IdConverter idConverter() {
        throw new UnsupportedOperationException("CosIdGenerator does not support IdConverter,please use CosIdIdStateParser instead!");
    }
    
    /**
     * Generate an ID as a long value (unsupported in CosIdGenerator).
     * 
     * <p>CosIdGenerator does not support generating simple long IDs because it
     * uses a state-based approach that includes structured information. This
     * method always throws {@link UnsupportedOperationException}.
     *
     * @return Never returns (always throws exception)
     * @throws UnsupportedOperationException always thrown because CosIdGenerator uses state-based generation
     */
    @Override
    default long generate() {
        throw new UnsupportedOperationException("CosIdGenerator does not support the generation of long IDs!");
    }
    
    /**
     * Generate an ID as a state object.
     * 
     * <p>This method generates a new ID as a {@link CosIdState} object containing
     * all the components (timestamp, machine ID, sequence) that make up the ID.
     * The state can then be converted to string format using the state parser.
     *
     * @return The generated ID as a state object
     */
    @NonNull
    CosIdState generateAsState();
    
    /**
     * Generate an ID as a string value.
     * 
     * <p>This method generates a new ID by first creating a {@link CosIdState}
     * and then converting it to string format using the configured
     * {@link #getStateParser()}.
     *
     * @return The generated ID as a string value
     */
    @Override
    default @NonNull String generateAsString() {
        return getStateParser().asString(generateAsState());
    }
    
    /**
     * Get statistical information about this CosId generator.
     * 
     * <p>This method provides detailed information about the generator's
     * configuration and current state, including machine ID and timestamp
     * information.
     *
     * @return Statistical information about this CosId generator
     */
    @Override
    default IdGeneratorStat stat() {
        return new CosIdGeneratorStat(getClass().getSimpleName(), getMachineId(), getLastTimestamp(), Stat.simple(getStateParser().getClass().getSimpleName()));
    }
}
