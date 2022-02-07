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

package me.ahoo.cosid.shardingsphere.sharding.utils;

import com.google.common.base.Preconditions;

import java.util.Properties;

/**
 * Properties tool class.
 *
 * @author ahoo wang
 */
public final class PropertiesUtil {

    /**
     * Get the value according to the key and verify whether the value exists. If it does not exist, an {@link IllegalArgumentException} will be thrown.
     *
     * @param properties The Properties
     * @param key        key of properties
     * @return value of key
     * @throws IllegalArgumentException throw an exception when the key does not exist
     */
    public static String getRequiredValue(Properties properties, String key) {
        Preconditions.checkArgument(properties.containsKey(key), "%s can not be null.", key);
        return properties.getProperty(key);
    }
}
