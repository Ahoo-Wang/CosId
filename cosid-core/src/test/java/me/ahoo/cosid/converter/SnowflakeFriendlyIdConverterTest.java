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

package me.ahoo.cosid.converter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SnowflakeId;

import me.ahoo.cosid.stat.converter.SnowflakeFriendlyIdConverterStat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author rocher kong
 */
class SnowflakeFriendlyIdConverterTest {
    @ParameterizedTest
    @ValueSource(longs = {295913926632165376L})
    void asString(long argId) {
        String idStr = SnowflakeFriendlyIdConverter.INSTANCE.asString(argId);
        Assertions.assertNotNull(idStr);
    }
    
    @ParameterizedTest
    @ValueSource(longs = {295913926632165376L})
    void asLong(long argId) {
        String idStr = SnowflakeFriendlyIdConverter.INSTANCE.asString(argId);
        long actual = SnowflakeFriendlyIdConverter.INSTANCE.asLong(idStr);
        Assertions.assertEquals(argId, actual);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"20220320133617924-5-0"})
    void asLong2(String argId) {
        long actual = SnowflakeFriendlyIdConverter.INSTANCE.asLong(argId);
        Assertions.assertNotNull(actual);
    }
    
    @Test
    void getParser() {
        SnowflakeId idGen = new SecondSnowflakeId(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            SecondSnowflakeId.DEFAULT_TIMESTAMP_BIT,
            SecondSnowflakeId.DEFAULT_MACHINE_BIT,
            SecondSnowflakeId.DEFAULT_SEQUENCE_BIT, 1023, 512);
        SecondSnowflakeIdStateParser snowflakeIdStateParser = SecondSnowflakeIdStateParser.of(idGen);
        SnowflakeFriendlyIdConverter snowflakeFriendlyIdConverter = new SnowflakeFriendlyIdConverter(snowflakeIdStateParser);
        Assertions.assertNotNull(snowflakeFriendlyIdConverter.getParser());
    }
    
    @Test
    void stat() {
        assertThat(SnowflakeFriendlyIdConverter.INSTANCE.stat(), instanceOf(SnowflakeFriendlyIdConverterStat.class));
    }
}
