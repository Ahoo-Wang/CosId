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

package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineIdLifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class CosIdLifecyclePrefetchWorkerExecutorServiceTest {
    
    @Test
    void stopsWithTheMachineIdsAfterTheWebServer() {
        PrefetchWorkerExecutorService executorService = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        CosIdLifecyclePrefetchWorkerExecutorService lifecycle = new CosIdLifecyclePrefetchWorkerExecutorService(executorService);
        assertThat(lifecycle.getPhase()).isEqualTo(CosIdMachineIdLifecycle.PHASE);
        
        lifecycle.start();
        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }
}
