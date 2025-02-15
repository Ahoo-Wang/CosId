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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.StringSnowflakeId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDecorator;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import java.time.ZoneId;

public class SnowflakeIdConverterDecorator extends IdConverterDecorator<SnowflakeId> {
    private final ZoneId zoneId;

    protected SnowflakeIdConverterDecorator(SnowflakeId idGenerator, IdConverterDefinition converterDefinition, ZoneId zoneId) {
        super(idGenerator, converterDefinition);
        this.zoneId = zoneId;
    }

    @Override
    protected IdConverter newSnowflakeFriendly() {
        return new SnowflakeFriendlyIdConverter(SnowflakeIdStateParser.of(idGenerator, zoneId, converterDefinition.getFriendly().isPadStart()));
    }

    @Override
    protected SnowflakeId newIdGenerator(IdConverter idConverter) {
        return new StringSnowflakeId(idGenerator, idConverter);
    }
}
