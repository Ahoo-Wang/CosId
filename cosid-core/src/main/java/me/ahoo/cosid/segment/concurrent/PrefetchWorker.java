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

import com.google.errorprone.annotations.ThreadSafe;

/**
 * Worker for prefetching ID segments.
 *
 * <p>Manages background prefetching of ID segments to ensure
 * segments are available before they are exhausted.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface PrefetchWorker {

    /**
     * Gets the worker name.
     *
     * @return the worker name
     */
    String getName();

    /**
     * Submits a job for prefetching.
     *
     * @param affinityJob the job to submit
     */
    void submit(AffinityJob affinityJob);

    /**
     * Cancels a prefetch job.
     *
     * @param affinityJob the job to cancel
     */
    void cancel(AffinityJob affinityJob);

    /**
     * Wakes up a job for immediate processing.
     *
     * @param affinityJob the job to wake up
     */
    void wakeup(AffinityJob affinityJob);

    /**
     * Shuts down the worker.
     */
    void shutdown();

}
