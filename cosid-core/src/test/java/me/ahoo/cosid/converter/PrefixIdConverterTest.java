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

/**
 * @author rocher kong
 */
class PrefixIdConverterTest {
    private static final String PREFIX = "prefix_";
    private final PrefixIdConverter idConverter = new PrefixIdConverter(PREFIX, ToStringIdConverter.INSTANCE);

    @Test
    void getPrefixShouldExposeConfiguredPrefix() {
        assertThat(idConverter.getPrefix(), equalTo(PREFIX));
    }

    @Test
    void getActualShouldExposeWrappedConverter() {
        assertThat(idConverter.getActual(), sameInstance(ToStringIdConverter.INSTANCE));
    }

    @Test
    void asStringShouldPrependPrefix() {
        String actual = idConverter.asString(42);

        assertThat(actual, equalTo(PREFIX + "42"));
    }

    @Test
    void asLongShouldRequireConfiguredPrefixAndDelegateSuffix() {
        long actual = idConverter.asLong(PREFIX + "42");

        assertThat(actual, equalTo(42L));
    }

    @Test
    void asLongShouldRejectMismatchedPrefix() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> idConverter.asLong("other_42"));

        assertThat(exception.getMessage(), containsString("prefix"));
    }

    @Test
    void asLongShouldDelegateNumericParsingFailures() {
        Assertions.assertEquals(-1L, idConverter.asLong("prefix_-1"));
        Assertions.assertEquals(111L, idConverter.asLong("prefix_111"));
        Assertions.assertThrows(NumberFormatException.class, () -> {
            idConverter.asLong("prefix_1_");
        });
    }

    @Test
    void emptyPrefixShouldLeaveStringRepresentationUnchanged() {
        PrefixIdConverter emptyPrefixConverter = new PrefixIdConverter("", ToStringIdConverter.INSTANCE);

        assertThat(emptyPrefixConverter.asString(42), equalTo("42"));
        assertThat(emptyPrefixConverter.asLong("42"), equalTo(42L));
    }

    @Test
    void statShouldExposePrefixAndActualConverter() {
        PrefixConverterStat stat = (PrefixConverterStat) idConverter.stat();

        assertThat(stat, instanceOf(PrefixConverterStat.class));
        assertThat(stat.getPrefix(), equalTo(PREFIX));
        assertThat(stat.getActual(), equalTo(ToStringIdConverter.INSTANCE.stat()));
    }
}
