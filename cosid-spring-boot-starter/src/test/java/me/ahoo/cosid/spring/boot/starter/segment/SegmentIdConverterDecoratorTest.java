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

package me.ahoo.cosid.spring.boot.starter.segment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.GroupedPrefixIdConverter;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;

public class SegmentIdConverterDecoratorTest {
    @Test
    void newSnowflakeFriendly() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setType(IdConverterDefinition.Type.SNOWFLAKE_FRIENDLY);
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate()
        );
    }

    @Test
    void newCustom() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setType(IdConverterDefinition.Type.CUSTOM);
        idConverterDefinition.setCustom(new IdConverterDefinition.Custom().setType(SegmentIdConverterDecoratorTest.CustomIdConverter.class));
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        SegmentId newIdGen = new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(SegmentIdConverterDecoratorTest.CustomIdConverter.class));
    }


    @Test
    void withPrefixAndSuffix() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setPrefix("prefix-");
        idConverterDefinition.setSuffix("suffix-");
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        SegmentId newIdGen = new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(SuffixIdConverter.class));
    }

    @Test
    void withGroupPrefix() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setGroupPrefix(new IdConverterDefinition.GroupPrefix().setEnabled(true));
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        SegmentId newIdGen = new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(GroupedPrefixIdConverter.class));
    }

    @Test
    void withYearPrefixAfterPrefix() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setGroupPrefix(new IdConverterDefinition.GroupPrefix().setEnabled(true).setBeforePrefix(false));
        idConverterDefinition.setPrefix("prefix-");
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        SegmentId newIdGen = new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(GroupedPrefixIdConverter.class));
    }

    @Test
    void withDatePrefix() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setDatePrefix(new IdConverterDefinition.DatePrefix().setEnabled(true));
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        SegmentId newIdGen = new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(DatePrefixIdConverter.class));
    }

    @Test
    void withDateNotBeforePrefix() {
        IdConverterDefinition idConverterDefinition = new IdConverterDefinition();
        idConverterDefinition.setDatePrefix(new IdConverterDefinition.DatePrefix().setEnabled(true).setBeforePrefix(false));
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        SegmentId newIdGen = new SegmentIdConverterDecorator(segmentId, idConverterDefinition).decorate();
        assertThat(newIdGen.idConverter(), instanceOf(DatePrefixIdConverter.class));
    }

    public static class CustomIdConverter implements IdConverter {
        @Nonnull
        @Override
        public String asString(long id) {
            return String.valueOf(id);
        }

        @Override
        public long asLong(@Nonnull String idString) {
            return Long.getLong(idString);
        }
    }
}

