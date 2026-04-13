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

package me.ahoo.cosid.snowflake;

import org.jspecify.annotations.NonNull;

/**
 * Snowflake ID with human-readable string representation.
 *
 * <p>Provides methods to convert Snowflake IDs to and from
 * a friendly string format containing timestamp, machine ID, and sequence.
 *
 * @author ahoo wang
 */
public interface SnowflakeFriendlyId extends SnowflakeId {

    /**
     * Gets the state parser.
     *
     * @return the parser
     */
    @NonNull
    SnowflakeIdStateParser getParser();

    /**
     * Parses a raw ID to friendly state.
     *
     * @param id the raw ID
     * @return the friendly state
     */
    @NonNull
    default SnowflakeIdState friendlyId(long id) {
        return getParser().parse(id);
    }

    /**
     * Generates an ID and returns its friendly state.
     *
     * @return the friendly state
     */
    @NonNull
    default SnowflakeIdState friendlyId() {
        long id = generate();
        return friendlyId(id);
    }

    /**
     * Parses a friendly ID string to state.
     *
     * @param friendlyId the friendly ID string
     * @return the friendly state
     */
    @NonNull
    default SnowflakeIdState ofFriendlyId(String friendlyId) {
        return getParser().parse(friendlyId);
    }

}
