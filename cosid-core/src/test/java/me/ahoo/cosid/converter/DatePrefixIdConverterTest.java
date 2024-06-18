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

import me.ahoo.cosid.stat.converter.DatePrefixConverterStat;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static me.ahoo.cosid.converter.GroupedPrefixIdConverter.DEFAULT_DELIMITER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

class DatePrefixIdConverterTest {
    private final DatePrefixIdConverter idConverter = new DatePrefixIdConverter("yyMMdd", DEFAULT_DELIMITER, ToStringIdConverter.INSTANCE);

    @Test
    void asString() {
        String idString = idConverter.asString(1);
        String expected = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + DEFAULT_DELIMITER + "1";
        assertThat(idString, equalTo(expected));
    }

    @Test
    void asLong() {
        assertThat(idConverter.asLong("240618-1"), equalTo(1L));
    }

    @Test
    void getActual() {
        assertThat(idConverter.getActual(), equalTo(ToStringIdConverter.INSTANCE));
    }

    @Test
    void stat() {
        assertThat(idConverter.stat(), instanceOf(DatePrefixConverterStat.class));
    }
}