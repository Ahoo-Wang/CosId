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

import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

@ExtendWith(OutputCaptureExtension.class)
class GuarderTimingTest {
    
    private static MachineProperties.Guarder guarder(String initialDelay, String delay, String safeGuard) {
        return new MachineProperties.Guarder()
            .setInitialDelay(Duration.parse(initialDelay))
            .setDelay(Duration.parse(delay))
            .setSafeGuardDuration(Duration.parse(safeGuard));
    }
    
    /**
     * Columns: initial-delay, delay, safe-guard-duration, store timeout, valid.
     */
    @ParameterizedTest
    @CsvSource({
        // The defaults.
        "PT1M, PT1M, PT5M, PT1S, true",
        "PT0S, PT10S, PT1M, PT1S, true",
        // Boundary: 5m - 10s margin = 4m50s; 4m49s + 1s is not shorter.
        "PT1M, PT4M49S, PT5M, PT1S, false",
        "PT1M, PT4M48S, PT5M, PT1S, true",
        // Boundary with a short safe guard: 10s - 1s margin = 9s.
        "PT0S, PT8S, PT10S, PT0S, true",
        "PT0S, PT9S, PT10S, PT0S, false",
        // initial-delay counts too.
        "PT5M, PT1M, PT5M, PT1S, false",
        // Non-positive values.
        "PT1M, PT0S, PT5M, PT1S, false",
        "PT1M, PT1M, PT0S, PT1S, false",
        "-PT1S, PT1M, PT5M, PT1S, false",
    })
    void checksThatTheLeaseIsRenewedBeforeItExpires(String initialDelay, String delay, String safeGuard, String storeTimeout, boolean valid) {
        String problem = GuarderTiming.check(guarder(initialDelay, delay, safeGuard), Duration.parse(storeTimeout));
        if (valid) {
            assertThat(problem).isNull();
        } else {
            assertThat(problem).isNotNull().contains(MachineProperties.PREFIX + ".guarder.");
        }
    }
    
    @Test
    void theStoreTimeoutDependsOnTheType() {
        MachineProperties.Distributor distributor = new MachineProperties().getDistributor();
        distributor.setType("redis");
        assertThat(GuarderTiming.storeTimeout(distributor, Duration.ofSeconds(5))).isEqualTo(distributor.getRedis().getTimeout());
        distributor.setType("proxy");
        assertThat(GuarderTiming.storeTimeout(distributor, Duration.ofSeconds(5))).isEqualTo(Duration.ofSeconds(5));
        distributor.setType("manual");
        assertThat(GuarderTiming.storeTimeout(distributor, Duration.ofSeconds(5))).isZero();
    }
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class))
        .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
        .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.PREFIX + ".instance-id=worker-a",
            MachineProperties.PREFIX + ".distributor.manual.machine-id=1", MachineProperties.PREFIX + ".guarder.safe-guard-duration=30s");
    
    @Test
    void startupFailsOnATooLongDelay() {
        contextRunner.run(context -> assertThat(context).hasFailed()
            .getFailure().rootCause().hasMessageContaining("[" + MachineProperties.PREFIX + ".guarder.safe-guard-duration]=PT30S"));
    }
    
    @Test
    void startupWarnsWithoutFailFast(CapturedOutput output) {
        contextRunner.withPropertyValues(MachineProperties.PREFIX + ".guarder.fail-fast=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MachineIdGuarder.class)).isNotSameAs(MachineIdGuarder.NONE);
            assertThat(output).contains("The guarder may renew the machine id lease too late");
        });
    }
}
