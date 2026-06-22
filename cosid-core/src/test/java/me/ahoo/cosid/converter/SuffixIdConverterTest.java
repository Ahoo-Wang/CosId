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

import me.ahoo.cosid.stat.converter.SuffixConverterStat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SuffixIdConverterTest .
 *
 * @author ahoo wang
 */
class SuffixIdConverterTest {
    private static final String SUFFIX = "-suffix";
    private final SuffixIdConverter idConverter = new SuffixIdConverter(SUFFIX, ToStringIdConverter.INSTANCE);

    @Test
    void getSuffixShouldExposeConfiguredSuffix() {
        assertThat(idConverter.getSuffix(), equalTo(SUFFIX));
    }

    @Test
    void getActualShouldExposeWrappedConverter() {
        assertThat(idConverter.getActual(), sameInstance(ToStringIdConverter.INSTANCE));
    }

    @Test
    void asStringShouldAppendSuffix() {
        String actual = idConverter.asString(42);

        assertThat(actual, equalTo("42" + SUFFIX));
    }

    @Test
    void asLongShouldRequireConfiguredSuffixAndDelegatePrefix() {
        long actual = idConverter.asLong("42" + SUFFIX);

        assertThat(actual, equalTo(42L));
    }

    @Test
    void asLongShouldRejectMismatchedSuffix() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> idConverter.asLong("42-other"));

        assertThat(exception.getMessage(), containsString("suffix"));
    }

    @Test
    void emptySuffixShouldLeaveStringRepresentationUnchanged() {
        SuffixIdConverter emptySuffixConverter = new SuffixIdConverter("", ToStringIdConverter.INSTANCE);

        assertThat(emptySuffixConverter.asString(42), equalTo("42"));
        assertThat(emptySuffixConverter.asLong("42"), equalTo(42L));
    }

    @Test
    void statShouldExposeSuffixAndActualConverter() {
        SuffixConverterStat stat = (SuffixConverterStat) idConverter.stat();

        assertThat(stat, instanceOf(SuffixConverterStat.class));
        assertThat(stat.getSuffix(), equalTo(SUFFIX));
        assertThat(stat.getActual(), equalTo(ToStringIdConverter.INSTANCE.stat()));
    }
}
