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

import me.ahoo.cosid.CosId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional annotation that enables beans when CosId is enabled.
 *
 * <p>This annotation can be applied to configuration classes or bean methods
 * to conditionally create them only when CosId auto-configuration is enabled.
 * By default, CosId is enabled unless explicitly disabled in configuration.</p>
 *
 * <p>The condition checks the property {@code cosid.enabled} which defaults to {@code true}.</p>
 *
 * <p>Example usage:
 * <pre>{@code
 * @Configuration
 * @ConditionalOnCosIdEnabled
 * public class MyCosIdConfiguration {
 *     // Beans created only when CosId is enabled
 * }
 * }</pre>
 *
 * @author ahoo wang
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(value = ConditionalOnCosIdEnabled.ENABLED_KEY, matchIfMissing = true, havingValue = "true")
public @interface ConditionalOnCosIdEnabled {
    /**
     * The configuration property key used to enable/disable CosId.
     */
    String ENABLED_KEY = CosId.COSID + EnabledSuffix.KEY;
}
