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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.GroupedPrefixIdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.junit.jupiter.api.Test;
import jakarta.annotation.Nonnull;

class SegmentIdConverterDecoratorTest {

    @Test
    void rejectsSnowflakeFriendlyConverterBecauseSegmentIdsHaveNoSnowflakeState() {
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setType(IdConverterDefinition.Type.SNOWFLAKE_FRIENDLY);

        assertThatThrownBy(() -> decorate(definition))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("newSnowflakeFriendly");
    }

    @Test
    void decoratesWithCustomConverter() {
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setType(IdConverterDefinition.Type.CUSTOM);
        definition.setCustom(new IdConverterDefinition.Custom().setType(CustomIdConverter.class));

        SegmentId decorated = decorate(definition);

        assertThat(decorated.idConverter()).isInstanceOf(CustomIdConverter.class);
        assertThat(decorated.idConverter().asString(42)).isEqualTo("custom-42");
        assertThat(decorated.idConverter().asLong("custom-42")).isEqualTo(42);
    }

    @Test
    void composesPrefixAndSuffixAroundTheBaseConverter() {
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setType(IdConverterDefinition.Type.TO_STRING);
        definition.setToString(new IdConverterDefinition.ToString());
        definition.setPrefix("seg-");
        definition.setSuffix("-done");

        IdConverter converter = decorate(definition).idConverter();

        assertThat(converter).isInstanceOf(SuffixIdConverter.class);
        assertThat(((Decorator<?>) converter).getActual()).isInstanceOf(PrefixIdConverter.class);
        assertThat(converter.asString(42)).isEqualTo("seg-42-done");
        assertThat(converter.asLong("seg-42-done")).isEqualTo(42);
    }

    @Test
    void composesDatePrefixBeforeStaticPrefixWhenConfiguredBeforePrefix() {
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setType(IdConverterDefinition.Type.TO_STRING);
        definition.setToString(new IdConverterDefinition.ToString());
        definition.setPrefix("seg-");
        definition.setDatePrefix(new IdConverterDefinition.DatePrefix()
            .setEnabled(true)
            .setPattern("yyyyMMdd")
            .setDelimiter("/")
            .setBeforePrefix(true));

        IdConverter converter = decorate(definition).idConverter();

        assertThat(converter).isInstanceOf(PrefixIdConverter.class);
        assertThat(((Decorator<?>) converter).getActual()).isInstanceOf(DatePrefixIdConverter.class);
        assertThat(converter.asLong("seg-20260101/42")).isEqualTo(42);
    }

    @Test
    void composesGroupPrefixAfterStaticPrefixWhenConfiguredAfterPrefix() {
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setType(IdConverterDefinition.Type.TO_STRING);
        definition.setToString(new IdConverterDefinition.ToString());
        definition.setPrefix("seg-");
        definition.setGroupPrefix(new IdConverterDefinition.GroupPrefix()
            .setEnabled(true)
            .setDelimiter("/")
            .setBeforePrefix(false));

        IdConverter converter = decorate(definition).idConverter();

        assertThat(converter).isInstanceOf(GroupedPrefixIdConverter.class);
        IdConverter actual = ((GroupedPrefixIdConverter) converter).getActual();
        assertThat(actual).isInstanceOf(PrefixIdConverter.class);
        assertThat(((Decorator<?>) actual).getActual()).isInstanceOf(ToStringIdConverter.class);
    }

    private static SegmentId decorate(IdConverterDefinition definition) {
        return new SegmentIdConverterDecorator(new DefaultSegmentId(new IdSegmentDistributor.Mock()), definition).decorate();
    }

    public static class CustomIdConverter implements IdConverter {
        @Nonnull
        @Override
        public String asString(long id) {
            return "custom-" + id;
        }

        @Override
        public long asLong(@Nonnull String idString) {
            return Long.parseLong(idString.substring("custom-".length()));
        }
    }
}
