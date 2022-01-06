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

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultPrefetchWorker extends Thread implements PrefetchWorker {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private volatile boolean shutdown = false;
    private final Duration prefetchPeriod;
    private final CopyOnWriteArraySet<AffinityJob> affinityJobs = new CopyOnWriteArraySet<>();

    public DefaultPrefetchWorker(Duration prefetchPeriod) {
        super(Strings.lenientFormat("DefaultPrefetchWorker-" + THREAD_COUNTER.incrementAndGet()));
        this.prefetchPeriod = prefetchPeriod;
    }

    @Override
    public void shutdown() {
        if (log.isInfoEnabled()) {
            log.info("shutdown!");
        }
        if (shutdown) {
            return;
        }
        shutdown = true;
    }

    @Override
    public void submit(AffinityJob affinityJob) {
        if (log.isInfoEnabled()) {
            log.info("submit - [{}] jobSize:[{}].", affinityJob.getJobId(), affinityJobs.size());
        }

        if (shutdown) {
            throw new IllegalArgumentException("PrefetchWorker is shutdown.");
        }
        affinityJobs.add(affinityJob);
    }

    @Override
    public void cancel(AffinityJob affinityJob) {
        if (log.isInfoEnabled()) {
            log.info("cancel - [{}] jobSize:[{}].", affinityJob.getJobId(), affinityJobs.size());
        }
        affinityJobs.remove(affinityJob);
    }

    @Override
    public void wakeup(AffinityJob affinityJob) {
        if (log.isDebugEnabled()) {
            log.debug("wakeup - [{}] - state:[{}].", affinityJob.getJobId(), this.getState());
        }
        if (shutdown) {
            if (log.isWarnEnabled()) {
                log.warn("wakeup - [{}] - PrefetchWorker is shutdown,Can't be awakened!", affinityJob.getJobId());
            }
            return;
        }

        if (State.RUNNABLE.equals(this.getState())) {
            if (log.isDebugEnabled()) {
                log.debug("wakeup - [{}] - PrefetchWorker is running ,Don't need to be awakened.", affinityJob.getJobId());
            }
            return;
        }
        LockSupport.unpark(this);
    }

    @Override
    public void run() {
        while (!shutdown) {
            try {
                affinityJobs.forEach(affinityJob -> {
                    try {
                        affinityJob.run();
                    } catch (Throwable throwable) {
                        if (log.isErrorEnabled()) {
                            log.error(throwable.getMessage(), throwable);
                        }
                    }
                });
                LockSupport.parkNanos(this, prefetchPeriod.toNanos());
            } catch (Throwable throwable) {
                if (log.isErrorEnabled()) {
                    log.error(throwable.getMessage(), throwable);
                }
            }
        }
    }
}
