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

package me.ahoo.cosid.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * {@link Codecs}: the grouped codec specs of a periodic segment, in the three 4.x {@code group-prefix} forms.
 */
class CodecsTest {
    private static final TimeSource NOV_1 = () -> Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();
    private final Codecs codecs = new Codecs(NOV_1, reference -> Optional.empty());
    private final GroupedId id = new GroupedId("20261026", 42);

    @Test
    void thePeriodFirst() {
        GroupedCodec codec = codecs.grouped(new GroupedCodecSpec.DatePrefix("-", List.of(new CodecSpec.Radix(10, 6, true), new CodecSpec.Prefix("O-"))),
            Period.YEAR_MONTH_DAY);
        assertThat(codec.encode(id), is("20261026-O-000042"));
        assertThat(codec.decode("20261026-O-000042"), is(id));
    }

    @Test
    void thePeriodAfterTheBusinessPrefix() {
        GroupedCodec codec = codecs.grouped(new GroupedCodecSpec.DatePrefix("-", List.of(new CodecSpec.Radix(10, 6, true)),
                List.of(new CodecSpec.DatePrefix("yyMM", "/", DatePrefix.Source.NOW, ZoneOffset.UTC), new CodecSpec.Prefix("O-"), new CodecSpec.Suffix("!"))),
            Period.YEAR_MONTH_DAY);
        assertThat(codec.encode(id), is("O-2611/20261026-000042!"));
        assertThat(codec.decode("O-2611/20261026-000042!"), is(id));
    }

    @Test
    void theDecorationsAroundThePeriodOnlyDecorate() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> codecs.grouped(
            new GroupedCodecSpec.DatePrefix("-", List.of(), List.of(new CodecSpec.Radix(62, 11, false))), Period.YEAR));
        assertThat(e.getMessage(), containsString("must be the first codec"));
    }

    @Test
    void theValueAlone() {
        GroupedCodec codec = codecs.grouped(new GroupedCodecSpec.ValueOnly(List.of(new CodecSpec.Prefix("O-"))), Period.YEAR_MONTH_DAY);
        assertThat(codec.encode(id), is("O-42"));
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> codec.decode("O-42"));
        assertThat(e.getMessage(), containsString("the period is not in the text"));
    }
}
