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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.segment.grouped.GroupedAccessor;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.stat.converter.GroupedPrefixConverterStat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GroupedPrefixIdConverterTest {

    @AfterEach
    void clearGroupedContext() {
        GroupedAccessor.clear();
    }

    @Test
    void asStringShouldPrefixCurrentGroupKeyAndDelegateIdConversion() {
        GroupedAccessor.set(GroupedKey.forever("2024"));
        GroupedPrefixIdConverter converter = new GroupedPrefixIdConverter(
            GroupedPrefixIdConverter.DEFAULT_DELIMITER,
            Radix62IdConverter.PAD_START
        );

        String actual = converter.asString(62);

        assertEquals("2024-00000000010", actual);
        assertEquals("-", converter.getDelimiter());
        assertSame(Radix62IdConverter.PAD_START, converter.getActual());
    }

    @Test
    void asStringShouldConcatenateGroupAndIdWhenDelimiterIsEmpty() {
        GroupedAccessor.set(GroupedKey.forever("tenantA"));
        GroupedPrefixIdConverter converter = new GroupedPrefixIdConverter("", ToStringIdConverter.INSTANCE);

        assertEquals("tenantA42", converter.asString(42));
    }

    @Test
    void asStringShouldRequireGroupedContext() {
        GroupedPrefixIdConverter converter = new GroupedPrefixIdConverter("-", ToStringIdConverter.INSTANCE);

        NullPointerException error = assertThrows(NullPointerException.class, () -> converter.asString(1));

        assertEquals("The current thread has not set the GroupedKey.", error.getMessage());
    }

    @Test
    void asLongShouldBeUnsupportedBecauseGroupPrefixIsNotReversible() {
        GroupedPrefixIdConverter converter = new GroupedPrefixIdConverter("-", ToStringIdConverter.INSTANCE);

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class, () -> converter.asLong("2024-1"));

        assertEquals("GroupedPrefixIdConverter does not support converting String to Long!", error.getMessage());
    }

    @Test
    void statShouldExposeDelimiterAndActualConverterStat() {
        GroupedPrefixIdConverter converter = new GroupedPrefixIdConverter("/", ToStringIdConverter.INSTANCE);

        GroupedPrefixConverterStat stat = assertInstanceOf(GroupedPrefixConverterStat.class, converter.stat());

        assertEquals("GroupedPrefixIdConverter", stat.getKind());
        assertEquals("/", stat.getDelimiter());
        assertEquals(ToStringIdConverter.INSTANCE.stat(), stat.getActual());
    }
}
