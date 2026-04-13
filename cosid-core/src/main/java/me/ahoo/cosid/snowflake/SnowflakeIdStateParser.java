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

import static me.ahoo.cosid.cosid.FriendlyIdStateParser.DECIMAL_RADIX;
import static me.ahoo.cosid.cosid.FriendlyIdStateParser.intAsString;

import me.ahoo.cosid.IdGeneratorDecorator;
import me.ahoo.cosid.converter.RadixIdConverter;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;


/**
 * Abstract parser for converting between Snowflake IDs and their component state.
 *
 * <p>This class provides methods to parse a snowflake ID into its components
 * (timestamp, machineId, sequence) and to reconstruct the raw ID from a
 * friendly string format.
 *
 * <p>The friendly format is: {@code timestamp-machineId-sequence} (e.g., "20210623131730192-1-0")
 *
 * @author ahoo wang
 */
@ThreadSafe
public abstract class SnowflakeIdStateParser {

    /**
     * Delimiter used in friendly ID format.
     */
    public static final String DELIMITER = "-";
    /**
     * Time zone used for timestamp conversion.
     */
    protected final ZoneId zoneId;
    /**
     * Epoch timestamp in milliseconds.
     */
    protected final long epoch;

    /**
     * Number of bits for sequence portion.
     */
    protected final int sequenceBit;
    /**
     * Mask for extracting sequence from raw ID.
     */
    protected final long sequenceMask;

    /**
     * Number of bits for machine ID portion.
     */
    protected final int machineBit;
    /**
     * Mask for extracting machine ID from raw ID.
     */
    protected final long machineMask;
    /**
     * Number of bits to shift machine ID left.
     */
    protected final int machineLeft;

    /**
     * Number of bits for timestamp portion.
     */
    protected final int timestampBit;
    /**
     * Mask for extracting timestamp from raw ID.
     */
    protected final long timestampMask;
    /**
     * Number of bits to shift timestamp left.
     */
    protected final int timestampLeft;
    /**
     * Whether to pad numeric fields with leading zeros.
     */
    protected final boolean padStart;
    private final int machineCharSize;
    private final int sequenceCharSize;

    /**
     * Creates a parser with default zone and no padding.
     *
     * @param epoch        epoch timestamp in milliseconds
     * @param timestampBit number of bits for timestamp
     * @param machineBit   number of bits for machine ID
     * @param sequenceBit number of bits for sequence
     */
    public SnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit) {
        this(epoch, timestampBit, machineBit, sequenceBit, ZoneId.systemDefault(), false);
    }

    /**
     * Creates a parser with custom zone and padding settings.
     *
     * @param epoch        epoch timestamp in milliseconds
     * @param timestampBit number of bits for timestamp
     * @param machineBit   number of bits for machine ID
     * @param sequenceBit number of bits for sequence
     * @param zoneId       time zone for timestamp conversion
     * @param padStart     whether to pad numeric fields with leading zeros
     */
    public SnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit, ZoneId zoneId, boolean padStart) {
        this.epoch = epoch;
        this.sequenceMask = getMask(sequenceBit);
        this.sequenceBit = sequenceBit;
        this.machineMask = getMask(machineBit);
        this.machineBit = machineBit;
        this.timestampMask = getMask(timestampBit);
        this.timestampBit = timestampBit;
        this.zoneId = zoneId;
        this.machineLeft = sequenceBit;
        this.timestampLeft = machineLeft + machineBit;
        this.padStart = padStart;
        this.machineCharSize = RadixIdConverter.maxCharSize(DECIMAL_RADIX, machineBit);
        this.sequenceCharSize = RadixIdConverter.maxCharSize(DECIMAL_RADIX, sequenceBit);
    }

    /**
     * Gets the time zone used for timestamp conversion.
     *
     * @return the zone ID
     */
    public ZoneId getZoneId() {
        return zoneId;
    }

    /**
     * Checks if numeric fields are padded with leading zeros.
     *
     * @return true if padding is enabled
     */
    public boolean isPadStart() {
        return padStart;
    }

    /**
     * Gets the maximum character size for machine ID in decimal representation.
     *
     * @return the machine ID character size
     */
    public int getMachineCharSize() {
        return machineCharSize;
    }

    /**
     * Gets the maximum character size for sequence in decimal representation.
     *
     * @return the sequence character size
     */
    public int getSequenceCharSize() {
        return sequenceCharSize;
    }

    /**
     * Gets the date time formatter for parsing timestamps.
     *
     * @return the date time formatter
     */
    protected abstract DateTimeFormatter getDateTimeFormatter();

    /**
     * Converts a time difference to a LocalDateTime.
     *
     * @param diffTime time difference from epoch in the appropriate unit
     * @return the corresponding LocalDateTime
     */
    protected abstract LocalDateTime getTimestamp(long diffTime);

    /**
     * Converts a LocalDateTime to time difference from epoch.
     *
     * @param timestamp the LocalDateTime to convert
     * @return time difference from epoch
     */
    protected abstract long getDiffTime(LocalDateTime timestamp);

    /**
     * Parses a friendly ID string into SnowflakeIdState.
     *
     * <p>Expected format: {@code timestamp-machineId-sequence}
     *
     * @param friendlyId the friendly ID string to parse
     * @return the parsed state
     * @throws IllegalArgumentException if format is invalid
     * @throws NullPointerException     if friendlyId is null
     */
    public SnowflakeIdState parse(String friendlyId) {
        Preconditions.checkNotNull(friendlyId, "friendlyId can not be null!");
        List<String> segments = Splitter.on(DELIMITER).trimResults().omitEmptyStrings().splitToList(friendlyId);
        if (segments.size() != 3) {
            throw new IllegalArgumentException(Strings.lenientFormat("friendlyId :[%s] Illegal.", friendlyId));
        }
        String timestampStr = segments.get(0);
        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, getDateTimeFormatter());
        long machineId = Long.parseLong(segments.get(1));
        long sequence = Long.parseLong(segments.get(2));
        long diffTime = getDiffTime(timestamp);
        /**
         * machineLeft greater than 30 will cause overflow, so machineId should be long when calculating.
         */
        long id = (diffTime) << timestampLeft
                | machineId << machineLeft
                | sequence;
        return SnowflakeIdState.builder()
                .id(id)
                .machineId((int) machineId)
                .sequence(sequence)
                .timestamp(timestamp)
                .friendlyId(friendlyId)
                .build();
    }

    /**
     * Parses a raw snowflake ID into SnowflakeIdState.
     *
     * @param id the raw snowflake ID
     * @return the parsed state
     */
    public SnowflakeIdState parse(long id) {
        int machineId = parseMachineId(id);
        long sequence = parseSequence(id);
        LocalDateTime timestamp = parseTimestamp(id);

        String friendlyId = new StringBuilder(timestamp.format(getDateTimeFormatter()))
                .append(DELIMITER)
                .append(intAsString(padStart, machineId, machineCharSize))
                .append(DELIMITER)
                .append(intAsString(padStart, (int) sequence, sequenceCharSize))
                .toString();

        return SnowflakeIdState.builder()
                .id(id)
                .machineId(machineId)
                .sequence(sequence)
                .timestamp(timestamp)
                .friendlyId(friendlyId)
                .build();
    }

    private long getMask(long bits) {
        return ~(-1L << bits);
    }

    /**
     * Extracts and parses the timestamp portion of an ID.
     *
     * @param id the raw snowflake ID
     * @return the parsed timestamp as LocalDateTime
     */
    public LocalDateTime parseTimestamp(long id) {
        long diffTime = (id >> timestampLeft) & timestampMask;
        return getTimestamp(diffTime);
    }

    /**
     * Extracts the machine ID portion of an ID.
     *
     * @param id the raw snowflake ID
     * @return the machine ID
     */
    public int parseMachineId(long id) {
        return (int) ((id >> machineLeft) & machineMask);
    }

    /**
     * Extracts the sequence portion of an ID.
     *
     * @param id the raw snowflake ID
     * @return the sequence number
     */
    public long parseSequence(long id) {
        return id & sequenceMask;
    }

    /**
     * Creates a parser for the given SnowflakeId instance.
     *
     * @param snowflakeId the snowflake ID to create a parser for
     * @return the appropriate parser for the snowflake ID type
     */
    public static SnowflakeIdStateParser of(SnowflakeId snowflakeId) {
        return of(snowflakeId, ZoneId.systemDefault(), false);
    }

    /**
     * Creates a parser for the given SnowflakeId instance with custom settings.
     *
     * @param snowflakeId the snowflake ID to create a parser for
     * @param zoneId      time zone for timestamp conversion
     * @param padStart    whether to pad numeric fields
     * @return the appropriate parser for the snowflake ID type
     */
    public static SnowflakeIdStateParser of(SnowflakeId snowflakeId, ZoneId zoneId, boolean padStart) {
        SnowflakeId actual = IdGeneratorDecorator.getActual(snowflakeId);

        if (actual instanceof SecondSnowflakeId) {
            return SecondSnowflakeIdStateParser.of(actual, zoneId, padStart);
        }
        return MillisecondSnowflakeIdStateParser.of(actual, zoneId, padStart);
    }
}
