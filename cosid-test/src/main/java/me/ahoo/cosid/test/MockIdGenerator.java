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

package me.ahoo.cosid.test;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.StringIdGeneratorDecorator;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock ID Generator for test.
 *
 * @author ahoo wang
 */
public class MockIdGenerator extends StringIdGeneratorDecorator {
    
    public static final String TEST_PREFIX = "test_";
    
    public static final IdGenerator INSTANCE = usePrefix(TEST_PREFIX);
    
    public MockIdGenerator(String prefix) {
        super(new MillisecondSnowflakeId(randomMachineId()), new PrefixIdConverter(prefix, Radix62IdConverter.INSTANCE));
    }
    
    /**
     * A random machine id, so that test JVMs running at the same time (Gradle forks, CI jobs on one database) do not
     * generate the same ids for the namespaces and names they share a backend with.
     */
    private static int randomMachineId() {
        return ThreadLocalRandom.current().nextInt(1 << MillisecondSnowflakeId.DEFAULT_MACHINE_BIT);
    }
    
    public static IdGenerator usePrefix(String prefix) {
        return new MockIdGenerator(prefix);
    }
}
