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

import me.ahoo.cosid.util.Clock;

/**
 * Job with affinity for prefetch worker assignment.
 *
 * <p>Represents a segment prefetch task that has affinity to a specific
 * worker instance for consistent segment allocation.
 *
 * @author ahoo wang
 */
public interface AffinityJob extends Runnable {

    /**
     * Gets the unique job identifier.
     *
     * @return the job ID
     */
    String getJobId();

    /**
     * Gets the affinity key for worker assignment.
     *
     * @return the affinity key (defaults to job ID)
     */
    default String affinity() {
        return getJobId();
    }

    /**
     * Signals this job is hungry and needs prefetch.
     */
    default void hungry() {
        setHungerTime(Clock.CACHE.secondTime());
        getPrefetchWorker().wakeup(this);
    }

    /**
     * Sets the hunger time for this job.
     *
     * @param hungerTime time in seconds since epoch
     */
    void setHungerTime(long hungerTime);

    /**
     * Gets the prefetch worker for this job.
     *
     * @return the prefetch worker
     */
    PrefetchWorker getPrefetchWorker();

    /**
     * Sets the prefetch worker for this job.
     *
     * @param prefetchWorker the worker to set
     */
    void setPrefetchWorker(PrefetchWorker prefetchWorker);

}
