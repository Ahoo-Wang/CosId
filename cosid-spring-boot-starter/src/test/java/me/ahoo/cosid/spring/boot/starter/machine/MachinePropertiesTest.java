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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.machine.LocalMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

class MachinePropertiesTest {

    @Test
    void defaultsKeepMachineSupportOptInAndManualDistributorUnconfigured() {
        MachineProperties properties = new MachineProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getStable()).isNull();
        assertThat(properties.getPort()).isNull();
        assertThat(properties.getInstanceId()).isNull();
        assertThat(properties.getMachineBit()).isEqualTo(MillisecondSnowflakeId.DEFAULT_MACHINE_BIT);
        assertThat(properties.getStateStorage().getLocal().getStateLocation())
            .isEqualTo(LocalMachineStateStorage.DEFAULT_STATE_LOCATION_PATH);
        assertThat(properties.getDistributor().getType()).isEqualTo(MachineProperties.Distributor.Type.MANUAL);
        assertThat(properties.getDistributor().getManual()).isNull();
        assertThat(properties.getDistributor().getRedis().getTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getDistributor().getMongo().getDatabase()).isEqualTo("cosid_db");
        assertThat(properties.getGuarder().isEnabled()).isTrue();
        assertThat(properties.getGuarder().getInitialDelay()).isEqualTo(DefaultMachineIdGuarder.DEFAULT_INITIAL_DELAY);
        assertThat(properties.getGuarder().getDelay()).isEqualTo(DefaultMachineIdGuarder.DEFAULT_DELAY);
        assertThat(properties.getGuarder().getSafeGuardDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getClockBackwards().getSpinThreshold())
            .isEqualTo(DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD);
        assertThat(properties.getClockBackwards().getBrokenThreshold())
            .isEqualTo(DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD);
    }

    @Test
    void binderMapsNestedMachineDistributorGuarderAndClockBackwardsProperties() {
        MachineProperties properties = bind(Map.ofEntries(
            Map.entry("cosid.machine.enabled", "true"),
            Map.entry("cosid.machine.stable", "true"),
            Map.entry("cosid.machine.port", "8080"),
            Map.entry("cosid.machine.instance-id", "instance-a"),
            Map.entry("cosid.machine.machine-bit", "9"),
            Map.entry("cosid.machine.state-storage.local.state-location", "/tmp/cosid-machine"),
            Map.entry("cosid.machine.distributor.type", "redis"),
            Map.entry("cosid.machine.distributor.manual.machine-id", "7"),
            Map.entry("cosid.machine.distributor.redis.timeout", "2s"),
            Map.entry("cosid.machine.distributor.mongo.database", "machine_db"),
            Map.entry("cosid.machine.guarder.enabled", "false"),
            Map.entry("cosid.machine.guarder.initial-delay", "3s"),
            Map.entry("cosid.machine.guarder.delay", "4s"),
            Map.entry("cosid.machine.guarder.safe-guard-duration", "5s"),
            Map.entry("cosid.machine.clock-backwards.spin-threshold", "11"),
            Map.entry("cosid.machine.clock-backwards.broken-threshold", "12")
        ));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getStable()).isTrue();
        assertThat(properties.getPort()).isEqualTo(8080);
        assertThat(properties.getInstanceId()).isEqualTo("instance-a");
        assertThat(properties.getMachineBit()).isEqualTo(9);
        assertThat(properties.getStateStorage().getLocal().getStateLocation()).isEqualTo("/tmp/cosid-machine");
        assertThat(properties.getDistributor().getType()).isEqualTo(MachineProperties.Distributor.Type.REDIS);
        assertThat(properties.getDistributor().getManual().getMachineId()).isEqualTo(7);
        assertThat(properties.getDistributor().getRedis().getTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getDistributor().getMongo().getDatabase()).isEqualTo("machine_db");
        assertThat(properties.getGuarder().isEnabled()).isFalse();
        assertThat(properties.getGuarder().getInitialDelay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getGuarder().getDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.getGuarder().getSafeGuardDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getClockBackwards().getSpinThreshold()).isEqualTo(11);
        assertThat(properties.getClockBackwards().getBrokenThreshold()).isEqualTo(12);
    }

    @Test
    void safeGuardDurationFallsBackToForeverWhenGuarderIsDisabled() {
        MachineProperties properties = new MachineProperties()
            .setGuarder(new MachineProperties.Guarder().setEnabled(false).setSafeGuardDuration(Duration.ofSeconds(5)));

        assertThat(properties.getSafeGuardDuration()).isEqualTo(MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
    }

    @Test
    void safeGuardDurationUsesConfiguredValueWhenGuarderIsEnabled() {
        MachineProperties properties = new MachineProperties()
            .setGuarder(new MachineProperties.Guarder().setEnabled(true).setSafeGuardDuration(Duration.ofSeconds(5)));

        assertThat(properties.getSafeGuardDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    private static MachineProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
            .bind(MachineProperties.PREFIX, MachineProperties.class)
            .get();
    }
}
