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

package me.ahoo.cosid.spring.boot.starter.cosid;

import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.DEFAULT_SEQUENCE_BIT;
import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.DEFAULT_SEQUENCE_RESET_THRESHOLD;
import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.DEFAULT_TIMESTAMP_BIT;
import static me.ahoo.cosid.cosid.RadixCosIdGenerator.DEFAULT_MACHINE_BIT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.ZoneId;
import java.util.Map;

class CosIdGeneratorPropertiesTest {

    @Test
    void defaultsKeepCosIdGeneratorOptInAndRadix62() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getType()).isEqualTo(CosIdGeneratorProperties.Type.RADIX62);
        assertThat(properties.getNamespace()).isNull();
        assertThat(properties.getMachineBit()).isEqualTo(DEFAULT_MACHINE_BIT);
        assertThat(properties.getTimestampBit()).isEqualTo(DEFAULT_TIMESTAMP_BIT);
        assertThat(properties.getSequenceBit()).isEqualTo(DEFAULT_SEQUENCE_BIT);
        assertThat(properties.getSequenceResetThreshold()).isEqualTo(DEFAULT_SEQUENCE_RESET_THRESHOLD);
        assertThat(properties.getZoneId()).isEqualTo(ZoneId.systemDefault());
        assertThat(properties.isPadStart()).isTrue();
    }

    @Test
    void binderMapsGeneratorTypeBitsZoneAndPadding() {
        CosIdGeneratorProperties properties = bind(Map.of(
            "cosid.generator.enabled", "true",
            "cosid.generator.type", "friendly",
            "cosid.generator.namespace", "tenant-a",
            "cosid.generator.machine-bit", "8",
            "cosid.generator.timestamp-bit", "42",
            "cosid.generator.sequence-bit", "14",
            "cosid.generator.sequence-reset-threshold", "1024",
            "cosid.generator.zone-id", "UTC",
            "cosid.generator.pad-start", "false"
        ));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getType()).isEqualTo(CosIdGeneratorProperties.Type.FRIENDLY);
        assertThat(properties.getNamespace()).isEqualTo("tenant-a");
        assertThat(properties.getMachineBit()).isEqualTo(8);
        assertThat(properties.getTimestampBit()).isEqualTo(42);
        assertThat(properties.getSequenceBit()).isEqualTo(14);
        assertThat(properties.getSequenceResetThreshold()).isEqualTo(1024);
        assertThat(properties.getZoneId()).isEqualTo(ZoneId.of("UTC"));
        assertThat(properties.isPadStart()).isFalse();
    }

    private static CosIdGeneratorProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
            .bind(CosIdGeneratorProperties.PREFIX, CosIdGeneratorProperties.class)
            .get();
    }
}
