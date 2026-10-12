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

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.ZoneId;
import java.util.Map;

class SnowflakeIdPropertiesTest {

    @Test
    void defaultsKeepSnowflakeOptInWithEnabledShareDefinition() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getZoneId()).isEqualTo(ZoneId.systemDefault());
        assertThat(properties.getEpoch()).isEqualTo(CosId.COSID_EPOCH);
        assertThat(properties.getProvider()).isEmpty();
        assertThat(properties.getShare().isEnabled()).isTrue();
        assertThat(properties.getShare().isClockSync()).isTrue();
        assertThat(properties.getShare().getTimestampUnit())
            .isEqualTo(SnowflakeIdProperties.IdDefinition.TimestampUnit.MILLISECOND);
        assertThat(properties.getShare().getTimestampBit()).isEqualTo(MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT);
        assertThat(properties.getShare().getSequenceBit()).isEqualTo(MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT);
        assertThat(properties.getShare().getSequenceResetThreshold())
            .isEqualTo(MillisecondSnowflakeId.DEFAULT_SEQUENCE_RESET_THRESHOLD);
        assertThat(properties.getShare().getConverter().getType()).isEqualTo(IdConverterDefinition.Type.RADIX);
    }

    @Test
    void binderMapsShareAndNamedProviderDefinitions() {
        SnowflakeIdProperties properties = bind(Map.ofEntries(
            Map.entry("cosid.snowflake.enabled", "true"),
            Map.entry("cosid.snowflake.zone-id", "UTC"),
            Map.entry("cosid.snowflake.epoch", "123456789"),
            Map.entry("cosid.snowflake.share.enabled", "false"),
            Map.entry("cosid.snowflake.provider.order.namespace", "orders"),
            Map.entry("cosid.snowflake.provider.order.clock-sync", "false"),
            Map.entry("cosid.snowflake.provider.order.timestamp-unit", "second"),
            Map.entry("cosid.snowflake.provider.order.epoch", "1000"),
            Map.entry("cosid.snowflake.provider.order.machine-bit", "9"),
            Map.entry("cosid.snowflake.provider.order.timestamp-bit", "31"),
            Map.entry("cosid.snowflake.provider.order.sequence-bit", "11"),
            Map.entry("cosid.snowflake.provider.order.sequence-reset-threshold", "512"),
            Map.entry("cosid.snowflake.provider.order.converter.type", "to_string"),
            Map.entry("cosid.snowflake.provider.order.converter.to-string.pad-start", "true")
        ));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getZoneId()).isEqualTo(java.time.ZoneId.of("UTC"));
        assertThat(properties.getEpoch()).isEqualTo(123456789);
        assertThat(properties.getShare().isEnabled()).isFalse();

        SnowflakeIdProperties.IdDefinition order = properties.getProvider().get("order");
        assertThat(order.getNamespace()).isEqualTo("orders");
        assertThat(order.isClockSync()).isFalse();
        assertThat(order.getTimestampUnit()).isEqualTo(SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND);
        assertThat(order.getEpoch()).isEqualTo(1000);
        assertThat(order.getMachineBit()).isEqualTo(9);
        assertThat(order.getTimestampBit()).isEqualTo(31);
        assertThat(order.getSequenceBit()).isEqualTo(11);
        assertThat(order.getSequenceResetThreshold()).isEqualTo(512);
        assertThat(order.getConverter().getType()).isEqualTo(IdConverterDefinition.Type.TO_STRING);
        assertThat(order.getConverter().getToString().isPadStart()).isTrue();
    }

    private static SnowflakeIdProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
            .bind(SnowflakeIdProperties.PREFIX, SnowflakeIdProperties.class)
            .get();
    }
}
