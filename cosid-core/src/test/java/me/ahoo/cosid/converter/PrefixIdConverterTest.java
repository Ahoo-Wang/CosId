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
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.stat.converter.PrefixConverterStat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author rocher kong
 */
class PrefixIdConverterTest {
    private static final String PREFIX = "prefix_";
    private final PrefixIdConverter idConverter = new PrefixIdConverter(PREFIX, ToStringIdConverter.INSTANCE);
    
    @Test
    void getSuffix() {
        assertThat(idConverter.getPrefix(), equalTo(PREFIX));
    }
    
    @Test
    void getActual() {
        assertThat(idConverter.getActual(), equalTo(ToStringIdConverter.INSTANCE));
    }
    
    @Test
    void asString() {
        long randomId = ThreadLocalRandom.current().nextLong();
        String actual = idConverter.asString(randomId);
        assertThat(actual, equalTo(PREFIX + randomId));
    }
    
    @Test
    void asLong() {
        long randomId = ThreadLocalRandom.current().nextLong();
        long actual = idConverter.asLong(PREFIX + randomId);
        assertThat(actual, equalTo(randomId));
    }
    
    @Test
    void asLongWhenNumberFormat() {
        Assertions.assertDoesNotThrow(() -> {
            idConverter.asLong("prefix_-1");
        });
        Assertions.assertThrows(StringIndexOutOfBoundsException.class, () -> {
            idConverter.asLong("-1");
        });
        Assertions.assertDoesNotThrow(() -> {
            idConverter.asLong("prefix_111");
        });
        Assertions.assertThrows(NumberFormatException.class, () -> {
            idConverter.asLong("prefix_1_");
        });
    }
    
    @Test
    void stat() {
        assertThat(idConverter.stat(), instanceOf(PrefixConverterStat.class));
    }
}
