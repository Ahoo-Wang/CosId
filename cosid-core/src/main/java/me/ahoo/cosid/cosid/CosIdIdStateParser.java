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

/**
 * Parser for converting {@link CosIdState} to String and vice versa.
 *
 * <p>The {@link CosIdState} is a composite of timestamp, machineId, and sequence.
 * This parser handles bidirectional conversion between the state object and string representations.
 *
 * @author ahoo wang
 */
public interface CosIdIdStateParser {

    /**
     * Parses a string representation into a CosIdState.
     *
     * @param id the string ID to parse
     * @return the parsed CosIdState
     */
    CosIdState asState(String id);

    /**
     * Converts timestamp, machineId, and sequence to a string.
     *
     * @param lastTimestamp the timestamp in milliseconds
     * @param machineId    the machine ID
     * @param sequence     the sequence number
     * @return string representation
     */
    String asString(long lastTimestamp, int machineId, int sequence);

    /**
     * Converts a CosIdState to its string representation.
     *
     * @param cosIdState the state to convert
     * @return string representation
     */
    default String asString(CosIdState cosIdState) {
        return asString(cosIdState.getTimestamp(), cosIdState.getMachineId(), cosIdState.getSequence());
    }

}
