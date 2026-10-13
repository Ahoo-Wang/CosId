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

package me.ahoo.cosid;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.cosid.Radix62CosIdGenerator;
import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.LazyIdGenerator;
import me.ahoo.cosid.provider.NotFoundIdGeneratorException;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SafeJavaScriptSnowflakeId;
import me.ahoo.cosid.uncertainty.UncertaintyIdGenerator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One regression test per small correctness fix.
 */
class SmallCorrectnessFixesTest {
    
    @Test
    void anEpochInTheFutureIsRejectedInsteadOfGivingNegativeIds() {
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(System.currentTimeMillis() + 86_400_000L, 41, 10, 12, 1);
        IllegalStateException exception = assertThrows(IllegalStateException.class, snowflakeId::generate);
        assertThat(exception.getMessage(), containsString("is after the current timestamp"));
    }
    
    @Test
    void radixCosIdGeneratorRejectsIntBitsOf32OrMore() {
        IllegalArgumentException sequence = assertThrows(IllegalArgumentException.class, () -> new Radix62CosIdGenerator(44, 10, 32, 1, 0));
        assertThat(sequence.getMessage(), containsString("sequenceBit:[32]"));
        IllegalArgumentException machine = assertThrows(IllegalArgumentException.class, () -> new Radix62CosIdGenerator(44, 32, 10, 1, 0));
        assertThat(machine.getMessage(), containsString("machineIdBit:[32]"));
    }
    
    @Test
    void datePrefixOfAnotherLengthThanItsPattern() {
        // Pattern "D" (day of the year) formats as 1 to 3 digits.
        DatePrefixIdConverter converter = new DatePrefixIdConverter("D", "-", Radix62IdConverter.PAD_START);
        String id = Radix62IdConverter.PAD_START.asString(12345L);
        assertThat(converter.asLong("286-" + id), equalTo(12345L));
        assertThat(converter.asLong("7-" + id), equalTo(12345L));
        assertThat(converter.asLong(converter.asString(12345L)), equalTo(12345L));
        // No delimiter within the longest date ("D" is at most 3 characters).
        assertThrows(IllegalArgumentException.class, () -> converter.asLong("2861" + id));
    }
    
    @Test
    void aFixedLengthDatePrefixWithoutDelimiterRoundTrips() {
        for (String pattern : new String[] {"yyyyMMdd", "uuuuMMddHHmmss", "yyMMdd"}) {
            DatePrefixIdConverter converter = new DatePrefixIdConverter(pattern, "", Radix62IdConverter.PAD_START);
            assertThat(pattern, converter.asLong(converter.asString(250L)), equalTo(250L));
        }
        DatePrefixIdConverter dashed = new DatePrefixIdConverter("yyyyMMdd", "-", Radix62IdConverter.PAD_START);
        assertThat(dashed.asLong(dashed.asString(250L)), equalTo(250L));
        assertThrows(IllegalArgumentException.class, () -> dashed.asLong("20261013"));
    }
    
    @Test
    void singleLetterMinutesAndSecondsAreVariableLength() {
        DatePrefixIdConverter converter = new DatePrefixIdConverter("H:m:s", "-", Radix62IdConverter.PAD_START);
        String id = Radix62IdConverter.PAD_START.asString(77L);
        assertThat(converter.asLong("9:5:7-" + id), equalTo(77L));
        assertThat(converter.asLong("23:59:59-" + id), equalTo(77L));
    }
    
    @Test
    void aDateThatContainsTheDelimiter() {
        Locale defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
        try {
            DatePrefixIdConverter converter = new DatePrefixIdConverter("MMMM-d", "-", Radix62IdConverter.PAD_START);
            String id = Radix62IdConverter.PAD_START.asString(99L);
            assertThat(converter.asLong("January-1-" + id), equalTo(99L));
            assertThat(converter.asLong("September-30-" + id), equalTo(99L));
            assertThat(converter.asLong(converter.asString(99L)), equalTo(99L));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, defaultLocale);
        }
    }
    
    @Test
    void aTextMonthUnderChineseLocaleWithADelimiter() {
        Locale defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.SIMPLIFIED_CHINESE);
        try {
            DatePrefixIdConverter converter = new DatePrefixIdConverter("yyyyMMMdd", "-", Radix62IdConverter.PAD_START);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMMdd", Locale.SIMPLIFIED_CHINESE);
            String id = Radix62IdConverter.PAD_START.asString(12345L);
            for (int month = 1; month <= 12; month++) {
                // "202610月13": October to December have a two-digit month in front of 月.
                String date = formatter.format(LocalDate.of(2026, month, 13));
                assertThat(date, converter.asLong(date + "-" + id), equalTo(12345L));
            }
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, defaultLocale);
        }
    }
    
    @Test
    void anEmptyStringIsNotAnId() {
        assertThrows(NumberFormatException.class, () -> Radix62IdConverter.PAD_START.asLong(""));
    }
    
    @Test
    void theLargestSafeNumberIsSafeInJavaScript() {
        assertThat(SafeJavaScriptSnowflakeId.isSafeJavaScript(SafeJavaScriptSnowflakeId.JAVA_SCRIPT_MAX_SAFE_NUMBER), is(true));
        assertThat(SafeJavaScriptSnowflakeId.isSafeJavaScript(SafeJavaScriptSnowflakeId.JAVA_SCRIPT_MAX_SAFE_NUMBER + 1), is(false));
    }
    
    @Test
    void theThresholdMessageNamesTheRightOrder() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DefaultClockBackwardsSynchronizer(10, 5));
        assertThat(exception.getMessage(), equalTo("brokenThreshold:[5] must be greater than spinThreshold:[10]!"));
    }
    
    @Test
    void theUncertaintyMessageNamesTheBits() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new UncertaintyIdGenerator(new MillisecondSnowflakeId(1), 0));
        assertThat(exception.getMessage(), equalTo("uncertaintyBits[0] must be greater than 0 and less than 63."));
    }
    
    @Test
    void aLazyGeneratorFollowsTheProvider() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        MillisecondSnowflakeId first = new MillisecondSnowflakeId(1);
        MillisecondSnowflakeId second = new MillisecondSnowflakeId(2);
        LazyIdGenerator lazy = new LazyIdGenerator("order", provider);
        provider.set("order", first);
        assertThat(lazy.getActual(), sameInstance(first));
        
        provider.set("order", second);
        assertThat(lazy.getActual(), sameInstance(second));
        
        provider.remove("order");
        assertThrows(NotFoundIdGeneratorException.class, lazy::getActual);
    }
}
