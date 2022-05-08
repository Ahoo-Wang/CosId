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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Properties;

/**
 * PropertiesUtilTest .
 *
 * @author ahoo wang
 */
class PropertiesUtilTest {
    
    @Test
    void assertGetRequiredValue() {
        Properties properties = new Properties();
        String key = "key";
        String expected = "string";
        properties.setProperty(key, expected);
        assertEquals(expected, PropertiesUtil.getRequiredValue(properties, key));
    }
    
    @Test
    void assertGetRequiredValueWhenKeyNotExists() {
        Properties properties = new Properties();
        String key = "key";
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            PropertiesUtil.getRequiredValue(properties, key);
        });
    }
    
    @Test
    public void assertGetRequiredValueWhenValueIsInt() {
        Properties properties = new Properties();
        properties.put("key", 1);
        String actual = PropertiesUtil.getRequiredValue(properties, "key");
        Assertions.assertEquals(actual, "1");
    }
}
