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

package me.ahoo.cosid.spring.boot.starter.metrics;

import me.ahoo.cosid.CosId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * Micrometer meters of CosId.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
@ConfigurationProperties(prefix = CosIdMetricsProperties.PREFIX)
public class CosIdMetricsProperties {
    public static final String PREFIX = CosId.COSID_PREFIX + "metrics";
    public static final String ENABLED_KEY = PREFIX + ".enabled";
    public static final String GENERATED_ENABLED_KEY = PREFIX + ".generated.enabled";
    static final String METER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry";
    
    /**
     * Register the CosId meters when Micrometer is on the classpath.
     */
    private boolean enabled = true;
    
    private Generated generated = new Generated();
    
    /**
     * Whether segment and Snowflake ids are created wrapped in a counting decorator: metrics on, the generated count
     * on, and Micrometer on the classpath.
     *
     * @param environment the environment
     * @return whether to count the generated ids
     */
    public static boolean isCountGenerated(Environment environment) {
        return environment.getProperty(ENABLED_KEY, Boolean.class, true)
            && environment.getProperty(GENERATED_ENABLED_KEY, Boolean.class, false)
            && ClassUtils.isPresent(METER_REGISTRY_CLASS, CosIdMetricsProperties.class.getClassLoader());
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Generated getGenerated() {
        return generated;
    }
    
    public void setGenerated(Generated generated) {
        this.generated = generated;
    }
    
    /**
     * The count of generated ids.
     */
    public static class Generated {
        /**
         * Count the ids each segment and Snowflake generator generates ({@code cosid.generator.generated}). Off by
         * default: it wraps those generators in a counting decorator, one atomic add per id.
         */
        private boolean enabled = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
