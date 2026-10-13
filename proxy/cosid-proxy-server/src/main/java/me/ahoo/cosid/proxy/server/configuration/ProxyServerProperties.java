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

package me.ahoo.cosid.proxy.server.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limits the proxy server applies to what clients send.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = ProxyServerProperties.PREFIX)
public class ProxyServerProperties {
    public static final String PREFIX = "cosid.proxy.server";
    public static final Duration DEFAULT_MAX_CLOCK_SKEW = Duration.ofMinutes(1);
    public static final Duration DEFAULT_MIN_SAFE_GUARD_DURATION = Duration.ofMinutes(5);
    /**
     * One trillion ids per request: more than a segment chain with a large step asks for while prefetching at peak, and
     * far from overflowing a {@code long}.
     */
    public static final long DEFAULT_MAX_SEGMENT_STEP = 1_000_000_000_000L;
    
    /**
     * How far ahead of the server's clock a client's {@code lastTimeStamp} (guard, revert) may be. A timestamp far in
     * the future would keep a machine id from ever being recycled, or make its next owner wait. Zero disables the check;
     * it must not be negative.
     */
    private Duration maxClockSkew = DEFAULT_MAX_CLOCK_SKEW;
    
    /**
     * The shortest safe guard duration the server accepts: a client may only take over a machine id whose lease was
     * last renewed at least this long ago (version 2 {@code expiredBefore}, version 1 {@code safeGuardDuration}).
     * Without it, any client could take a live lease and generate the same ids as its owner. Lower it only if your
     * clients use a shorter {@code cosid.machine.guarder.safe-guard-duration}. Default is 5 minutes, the clients'
     * default; zero still rejects cut-offs in the future. It must not be negative: a negative minimum would accept
     * cut-offs in the future, and a client could take a live lease.
     */
    private Duration minSafeGuardDuration = DEFAULT_MIN_SAFE_GUARD_DURATION;
    
    private Segment segment = new Segment();
    
    private Blocking blocking = new Blocking();
    
    public Duration getMinSafeGuardDuration() {
        return minSafeGuardDuration;
    }
    
    public void setMinSafeGuardDuration(Duration minSafeGuardDuration) {
        this.minSafeGuardDuration = nonNegative(minSafeGuardDuration, "min-safe-guard-duration");
    }
    
    public Duration getMaxClockSkew() {
        return maxClockSkew;
    }
    
    public void setMaxClockSkew(Duration maxClockSkew) {
        this.maxClockSkew = nonNegative(maxClockSkew, "max-clock-skew");
    }
    
    private static Duration nonNegative(Duration duration, String property) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("[" + PREFIX + "." + property + "] must not be null or negative, but was [" + duration + "].");
        }
        return duration;
    }
    
    public Segment getSegment() {
        return segment;
    }
    
    public void setSegment(Segment segment) {
        this.segment = segment;
    }
    
    public Blocking getBlocking() {
        return blocking;
    }
    
    public void setBlocking(Blocking blocking) {
        this.blocking = blocking;
    }
    
    public static class Segment {
        /**
         * Largest {@code step} a client may ask for, so that one request cannot use up or overflow the id range.
         * Default is one trillion; zero means no limit. Segment chains ask for {@code step × segments} at once and the prefetch distance grows under
         * load, so a limit must be at least the largest {@code step × max prefetch distance} of all clients.
         */
        private long maxStep = DEFAULT_MAX_SEGMENT_STEP;
        
        public long getMaxStep() {
            return maxStep;
        }
        
        public void setMaxStep(long maxStep) {
            this.maxStep = maxStep;
        }
    }
    
    /**
     * The thread pool that runs the API calls, which block on the machine-id and segment stores, so that a slow store
     * never stalls the event loop that also answers health checks.
     */
    public static class Blocking {
        public static final int DEFAULT_THREADS = 64;
        public static final int DEFAULT_QUEUE_CAPACITY = 1024;
        
        /**
         * Threads running API calls.
         */
        private int threads = DEFAULT_THREADS;
        /**
         * API calls waiting for a thread. Calls beyond it are answered with 503 at once.
         */
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
        
        public int getThreads() {
            return threads;
        }
        
        public void setThreads(int threads) {
            this.threads = threads;
        }
        
        public int getQueueCapacity() {
            return queueCapacity;
        }
        
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
