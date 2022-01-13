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

package me.ahoo.cosid.segment.concurrent;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prefetch Worker Executor Service.
 *
 * @author ahoo wang
 */
@Slf4j
public class PrefetchWorkerExecutorService {
    public static final Duration DEFAULT_PREFETCH_PERIOD = Duration.ofSeconds(1);
    public static final PrefetchWorkerExecutorService DEFAULT;

    static {
        DEFAULT =
                new PrefetchWorkerExecutorService(DEFAULT_PREFETCH_PERIOD, Runtime.getRuntime().availableProcessors());
    }

    private volatile boolean shutdown = false;
    private final int corePoolSize;
    private final Duration prefetchPeriod;
    private final DefaultPrefetchWorker[] workers;
    private boolean initialized = false;
    private final AtomicLong threadIdx = new AtomicLong();

    public PrefetchWorkerExecutorService(Duration prefetchPeriod, int corePoolSize) {
        Preconditions.checkArgument(corePoolSize > 0, "corePoolSize:[%s] must be greater than 0.", corePoolSize);
        this.prefetchPeriod = prefetchPeriod;
        this.corePoolSize = corePoolSize;
        this.workers = new DefaultPrefetchWorker[corePoolSize];
        Runtime.getRuntime().addShutdownHook(new GracefullyCloser());
    }

    private void ensureInitWorkers() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (int i = 0; i < corePoolSize; i++) {
            final DefaultPrefetchWorker prefetchWorker = new DefaultPrefetchWorker(prefetchPeriod);
            prefetchWorker.setDaemon(true);
            workers[i] = prefetchWorker;
            if (log.isDebugEnabled()) {
                log.debug("initWorkers - [{}].", prefetchWorker.getName());
            }
        }
    }

    public void shutdown() {
        if (log.isInfoEnabled()) {
            log.info("shutdown!");
        }
        if (shutdown) {
            return;
        }
        shutdown = true;

        for (DefaultPrefetchWorker worker : workers) {
            if (worker != null) {
                worker.shutdown();
            }
        }
    }

    public void submit(AffinityJob affinityJob) {
        Preconditions.checkNotNull(affinityJob, "affinityJob can not be null!");
        if (log.isInfoEnabled()) {
            log.info("submit - jobId:[{}].", affinityJob.getJobId());
        }
        if (shutdown) {
            throw new IllegalArgumentException("PrefetchWorkerExecutorService is shutdown.");
        }
        if (affinityJob.getPrefetchWorker() != null) {
            return;
        }
        synchronized (this) {
            if (affinityJob.getPrefetchWorker() != null) {
                return;
            }
            ensureInitWorkers();
            DefaultPrefetchWorker prefetchWorker = chooseWorker();
            if (log.isInfoEnabled()) {
                log.info("submit - jobId:[{}] is bound to thread:[{}].", affinityJob.getJobId(), prefetchWorker.getName());
            }

            if (Thread.State.NEW.equals(prefetchWorker.getState())) {
                if (log.isInfoEnabled()) {
                    log.info("submit - jobId:[{}] is bound to thread:[{}] start.", affinityJob.getJobId(), prefetchWorker.getName());
                }
                prefetchWorker.start();
            }
            prefetchWorker.submit(affinityJob);
            affinityJob.setPrefetchWorker(prefetchWorker);
        }
    }

    private DefaultPrefetchWorker chooseWorker() {
        return workers[(int) Math.abs(threadIdx.getAndIncrement() % workers.length)];
    }

    public class GracefullyCloser extends Thread {
        @Override
        public void run() {
            if (log.isInfoEnabled()) {
                log.info("Close gracefully!");
            }
            shutdown();
        }
    }
}
