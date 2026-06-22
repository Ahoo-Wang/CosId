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
import static org.hamcrest.Matchers.sameInstance;

class DatePrefixIdConverterTest {
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2024, 6, 18, 9, 30);
    private final DatePrefixIdConverter idConverter = new FixedDatePrefixIdConverter("yyMMdd", DEFAULT_DELIMITER, ToStringIdConverter.INSTANCE);

    @Test
    void asStringShouldPrefixFormattedDate() {
        String idString = idConverter.asString(1);
        String expected = FIXED_NOW.format(DateTimeFormatter.ofPattern("yyMMdd")) + DEFAULT_DELIMITER + "1";

        assertThat(idString, equalTo(expected));
    }

    @Test
    void asLongShouldRemoveDatePrefixAndDelegateSuffix() {
        assertThat(idConverter.asLong("240618-1"), equalTo(1L));
    }

    @Test
    void asLongShouldRejectStringShorterThanPrefix() {
        org.junit.jupiter.api.Assertions.assertThrows(StringIndexOutOfBoundsException.class, () -> idConverter.asLong("240"));
    }

    @Test
    void getActualShouldExposeWrappedConverter() {
        assertThat(idConverter.getActual(), sameInstance(ToStringIdConverter.INSTANCE));
    }

    @Test
    void statShouldExposePatternAndActualConverter() {
        DatePrefixConverterStat stat = (DatePrefixConverterStat) idConverter.stat();

        assertThat(stat, instanceOf(DatePrefixConverterStat.class));
        assertThat(stat.getPattern(), equalTo("yyMMdd"));
        assertThat(stat.getActual(), equalTo(ToStringIdConverter.INSTANCE.stat()));
    }

    private static final class FixedDatePrefixIdConverter extends DatePrefixIdConverter {
        private FixedDatePrefixIdConverter(String pattern, String delimiter, me.ahoo.cosid.IdConverter actual) {
            super(pattern, delimiter, actual);
        }

        @Override
        LocalDateTime now() {
            return FIXED_NOW;
        }
    }
}
