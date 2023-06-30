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

import static me.ahoo.cosid.converter.GroupedPrefixIdConverter.DEFAULT_DELIMITER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.segment.grouped.GroupedAccessor;
import me.ahoo.cosid.segment.grouped.GroupedKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GroupedPrefixIdConverterTest {
    GroupedPrefixIdConverter converter = new GroupedPrefixIdConverter(DEFAULT_DELIMITER, ToStringIdConverter.INSTANCE);
    
    @Test
    void asString() {
        GroupedAccessor.set(GroupedKey.forever("2023"));
        assertThat(converter.getDelimiter(), equalTo("-"));
        assertThat(converter.asString(1), equalTo("2023-1"));
    }
    
    @Test
    void asLong() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> converter.asLong("2023-1"));
    }
}