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

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;

import org.jspecify.annotations.NonNull;

import java.time.ZoneId;

/**
 * Default implementation of {@link SnowflakeFriendlyId}.
 *
 * <p>Wraps a {@link SnowflakeId} and provides human-readable
 * string conversion using a {@link SnowflakeIdStateParser}.
 *
 * @author ahoo wang
 */
public class DefaultSnowflakeFriendlyId extends StringSnowflakeId implements SnowflakeFriendlyId {

    private final SnowflakeIdStateParser snowflakeIdStateParser;

    /**
     * Creates an instance with system default zone.
     *
     * @param actual the underlying Snowflake ID
     */
    public DefaultSnowflakeFriendlyId(SnowflakeId actual) {
        this(actual, ZoneId.systemDefault());
    }

    /**
     * Creates an instance with specified zone.
     *
     * @param actual the underlying Snowflake ID
     * @param zoneId the time zone
     */
    public DefaultSnowflakeFriendlyId(SnowflakeId actual, ZoneId zoneId) {
        this(actual, SnowflakeIdStateParser.of(actual, zoneId, false));
    }

    /**
     * Creates an instance with specified parser.
     *
     * @param actual the underlying Snowflake ID
     * @param snowflakeIdStateParser the state parser
     */
    public DefaultSnowflakeFriendlyId(SnowflakeId actual, SnowflakeIdStateParser snowflakeIdStateParser) {
        this(actual, new SnowflakeFriendlyIdConverter(snowflakeIdStateParser), snowflakeIdStateParser);
    }

    /**
     * Creates an instance with specified converter and parser.
     *
     * @param actual the underlying Snowflake ID
     * @param converter the ID converter
     * @param snowflakeIdStateParser the state parser
     */
    public DefaultSnowflakeFriendlyId(SnowflakeId actual, IdConverter converter, SnowflakeIdStateParser snowflakeIdStateParser) {
        super(actual, converter);
        this.snowflakeIdStateParser = snowflakeIdStateParser;
    }

    @Override
    public @NonNull SnowflakeIdStateParser getParser() {
        return snowflakeIdStateParser;
    }

}
