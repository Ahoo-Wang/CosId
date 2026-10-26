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

package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.test.ShutdownHooks;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.TimeUnit;

/**
 * Opens and closes a starter context again and again: each close must stop the threads and remove the shutdown hooks
 * the context started, as an application that restarts its context (tests, devtools) does.
 */
class ContextRestartLeakTest {
    private static final int RESTARTS = 5;
    private static final String[] COSID_THREAD_PREFIXES = {"MachineIdGuarder", "DefaultPrefetchWorker"};
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class,
            CosIdSegmentAutoConfiguration.class))
        .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
        .withBean(IdSegmentDistributorFactory.class, () -> definition -> new AtomicIdSegmentDistributor())
        .withPropertyValues(
            ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true",
            MachineProperties.PREFIX + ".instance-id=worker-a",
            MachineProperties.PREFIX + ".distributor.manual.machine-id=7",
            ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true"
        );

    @Test
    void closingTheContextLeavesNoThreadsOrShutdownHooksBehind() throws InterruptedException {
        int hooksBefore = ShutdownHooks.count();
        int cosIdThreadsBefore = cosIdThreads();

        for (int i = 0; i < RESTARTS; i++) {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(IdGeneratorProvider.class).getShare().generate()).isPositive();
                assertThat(cosIdThreads()).isGreaterThan(cosIdThreadsBefore);
            });
        }

        assertThat(ShutdownHooks.count()).as("shutdown hooks").isEqualTo(hooksBefore);
        assertThat(awaitCosIdThreads(cosIdThreadsBefore)).as("CosId threads").isEqualTo(cosIdThreadsBefore);
    }

    @Test
    void shutdownHookCanStillBeTurnedOn() {
        int hooksBefore = ShutdownHooks.count();

        contextRunner
            .withPropertyValues(SegmentIdProperties.PREFIX + ".chain.prefetch-worker.shutdown-hook=true")
            .run(context -> assertThat(ShutdownHooks.count()).isEqualTo(hooksBefore + 1));

        assertThat(ShutdownHooks.count()).isEqualTo(hooksBefore);
    }

    private static int awaitCosIdThreads(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int threads = cosIdThreads();
        while (threads > expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
            threads = cosIdThreads();
        }
        return threads;
    }

    private static int cosIdThreads() {
        int threads = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && isCosIdThread(thread.getName())) {
                threads++;
            }
        }
        return threads;
    }

    private static boolean isCosIdThread(String name) {
        for (String prefix : COSID_THREAD_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
