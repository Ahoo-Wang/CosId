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

package me.ahoo.cosid.converter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

/**
 * SuffixIdConverterTest .
 *
 * @author ahoo wang
 */
class SuffixIdConverterTest {
    private static final String SUFFIX = "-suffix";
    private final SuffixIdConverter idConverter = new SuffixIdConverter(SUFFIX, ToStringIdConverter.INSTANCE);
    
    @Test
    void getSuffix() {
        assertThat(idConverter.getSuffix(), equalTo(SUFFIX));
    }
    
    @Test
    void getActual() {
        assertThat(idConverter.getActual(), equalTo(ToStringIdConverter.INSTANCE));
    }
    
    @Test
    void asString() {
        long randomId = ThreadLocalRandom.current().nextLong();
        String actual = idConverter.asString(randomId);
        assertThat(actual, equalTo(randomId + SUFFIX));
    }
    
    @Test
    void asLong() {
        long randomId = ThreadLocalRandom.current().nextLong();
        long actual = idConverter.asLong(randomId + SUFFIX);
        assertThat(actual, equalTo(randomId));
    }
}
