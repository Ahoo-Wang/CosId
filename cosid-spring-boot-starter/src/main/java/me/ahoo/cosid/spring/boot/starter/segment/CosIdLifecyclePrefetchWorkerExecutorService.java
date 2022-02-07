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

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

/**
 * CosId Lifecycle Prefetch Worker Executor Service.
 *
 * @author ahoo wang
 */
@Slf4j
public class CosIdLifecyclePrefetchWorkerExecutorService implements SmartLifecycle {
    private volatile boolean running;
    private final PrefetchWorkerExecutorService prefetchWorkerExecutorService;

    public CosIdLifecyclePrefetchWorkerExecutorService(PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        this.prefetchWorkerExecutorService = prefetchWorkerExecutorService;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        prefetchWorkerExecutorService.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
