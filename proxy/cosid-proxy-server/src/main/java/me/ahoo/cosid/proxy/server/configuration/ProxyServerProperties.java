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
    public static final long DEFAULT_MAX_SEGMENT_STEP = 1_000_000;
    
    /**
     * How far ahead of the server's clock a client's {@code lastTimeStamp} (guard, revert) may be. A timestamp far in
     * the future would keep a machine id from ever being recycled, or make its next owner wait. Zero disables the check.
     */
    private Duration maxClockSkew = DEFAULT_MAX_CLOCK_SKEW;
    
    private Segment segment = new Segment();
    
    public Duration getMaxClockSkew() {
        return maxClockSkew;
    }
    
    public void setMaxClockSkew(Duration maxClockSkew) {
        this.maxClockSkew = maxClockSkew;
    }
    
    public Segment getSegment() {
        return segment;
    }
    
    public void setSegment(Segment segment) {
        this.segment = segment;
    }
    
    public static class Segment {
        /**
         * Largest {@code step} a client may ask for, so that one request cannot use up the id range.
         */
        private long maxStep = DEFAULT_MAX_SEGMENT_STEP;
        
        public long getMaxStep() {
            return maxStep;
        }
        
        public void setMaxStep(long maxStep) {
            this.maxStep = maxStep;
        }
    }
}
