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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

class SnowflakeIdConverterDecoratorTest {
    
    @Test
    void newSnowflakeFriendly() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setType(IdConverterDefinition.Type.SNOWFLAKE_FRIENDLY);
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
        SnowflakeId newIdGen = new SnowflakeIdConverterDecorator(snowflakeId, idConverterDefinition, ZoneId.systemDefault()).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(SnowflakeFriendlyIdConverter.class));
    }
}