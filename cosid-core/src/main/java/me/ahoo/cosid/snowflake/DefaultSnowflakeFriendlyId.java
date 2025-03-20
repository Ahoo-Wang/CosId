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

import jakarta.annotation.Nonnull;

import java.time.ZoneId;

/**
 * Default Snowflake FriendlyId.
 *
 * @author ahoo wang
 */
public class DefaultSnowflakeFriendlyId extends StringSnowflakeId implements SnowflakeFriendlyId {

    private final SnowflakeIdStateParser snowflakeIdStateParser;

    public DefaultSnowflakeFriendlyId(SnowflakeId actual) {
        this(actual, ZoneId.systemDefault());
    }

    public DefaultSnowflakeFriendlyId(SnowflakeId actual, ZoneId zoneId) {
        this(actual, SnowflakeIdStateParser.of(actual, zoneId, false));
    }

    public DefaultSnowflakeFriendlyId(SnowflakeId actual, SnowflakeIdStateParser snowflakeIdStateParser) {
        this(actual, new SnowflakeFriendlyIdConverter(snowflakeIdStateParser), snowflakeIdStateParser);
    }

    public DefaultSnowflakeFriendlyId(SnowflakeId actual, IdConverter converter, SnowflakeIdStateParser snowflakeIdStateParser) {
        super(actual, converter);
        this.snowflakeIdStateParser = snowflakeIdStateParser;
    }

    @Nonnull
    @Override
    public SnowflakeIdStateParser getParser() {
        return snowflakeIdStateParser;
    }

}
