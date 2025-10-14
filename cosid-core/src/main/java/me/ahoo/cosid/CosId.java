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

import java.time.LocalDateTime;

/**
 * CosId constants and configuration values.
 * 
 * <p>This class contains global constants used throughout the CosId library,
 * including the epoch timestamp that serves as the base time for time-based
 * ID generators like Snowflake.
 * 
 * <p>The epoch is set to December 24, 2019, 16:00:00 UTC, which provides a
 * good balance between maximizing the available time bits and keeping IDs
 * relatively compact for the foreseeable future.
 *
 * @author ahoo wang
 */
public final class CosId {
    /**
     * The base name for CosId components and configuration properties.
     */
    public static final String COSID = "cosid";
    
    /**
     * The prefix used for CosId-related configuration properties.
     * 
     * <p>This is typically used when building configuration keys by appending
     * specific component names or properties to this prefix.
     */
    public static final String COSID_PREFIX = COSID + ".";
    
    /**
     * UTC EPOCH DATE of CosId.
     * 
     * <p>This is the base timestamp used by time-based ID generators in the CosId
     * library. It represents December 24, 2019, 16:00:00 UTC, which serves as
     * the starting point for calculating time differences in ID generation.
     * 
     * <p>Using a custom epoch rather than the Unix epoch (January 1, 1970) allows
     * time-based ID generators to use fewer bits for the timestamp portion,
     * leaving more bits available for other components like machine ID or sequence.
     */
    public static final LocalDateTime COSID_EPOCH_DATE;
    
    /**
     * COSID_EPOCH in milliseconds: 1577203200000.
     * 
     * <p>This is the millisecond representation of the CosId epoch timestamp.
     * Time-based ID generators use this value as the offset when calculating
     * the time portion of generated IDs.
     */
    public static final long COSID_EPOCH = 1577203200000L;
    
    /**
     * COSID_EPOCH in seconds: 1577203200.
     * 
     * <p>This is the second representation of the CosId epoch timestamp.
     * It's provided as a convenience for implementations that work with
     * second-precision timestamps.
     */
    public static final long COSID_EPOCH_SECOND = 1577203200L;

    static {
        COSID_EPOCH_DATE = LocalDateTime.of(2019, 12, 24, 16, 0);
    }
    
    /**
     * Private constructor to prevent instantiation.
     * 
     * <p>This class is a utility class containing only constants and should
     * not be instantiated.
     */
    private CosId() {
    }
}
