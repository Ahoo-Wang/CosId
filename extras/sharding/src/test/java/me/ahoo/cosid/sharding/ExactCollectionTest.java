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

package me.ahoo.cosid.sharding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author ahoo wang
 */
class ExactCollectionTest {

    @Test
    void ctorCreatesFixedSizeCollectionWithEmptySlots() {
        ExactCollection<String> exactCollection = new ExactCollection<>(3);

        assertEquals(3, exactCollection.size());
        assertArrayEquals(new Object[]{null, null, null}, exactCollection.toArray());
    }

    @Test
    void addStoresElementAtExactIndexWithoutChangingSize() {
        ExactCollection<String> exactCollection = new ExactCollection<>(3);

        exactCollection.add(1, "one");

        assertEquals(3, exactCollection.size());
        assertArrayEquals(new Object[]{null, "one", null}, exactCollection.toArray());
        assertEquals("one", exactCollection.get(1));
    }

    @Test
    void iteratorReturnsEverySlotInIndexOrder() {
        ExactCollection<String> exactCollection = new ExactCollection<>("zero", "one", "two");

        assertEquals(List.of("zero", "one", "two"), exactCollection.stream().toList());
        assertThrows(NoSuchElementException.class, () -> {
            var iterator = exactCollection.iterator();
            iterator.next();
            iterator.next();
            iterator.next();
            iterator.next();
        });
    }

    @Test
    void equalsUsesCollectionMembershipNotIndexOrder() {
        ExactCollection<String> exactCollection = new ExactCollection<>(2);
        exactCollection.add(0, "0");
        exactCollection.add(1, "1");

        assertEquals(new ExactCollection<>("0", "1"), exactCollection);
        assertEquals(new ExactCollection<>("1", "0"), exactCollection);
        assertEquals(new ExactCollection<>("0", "1").hashCode(), exactCollection.hashCode());
        assertEquals(new ExactCollection<>("1", "0").hashCode(), exactCollection.hashCode());
    }

    @Test
    void notEqualsCollectionsWithDifferentSizeOrMembers() {
        ExactCollection<String> exactCollection = new ExactCollection<>("0", "1");

        assertFalse(exactCollection.equals(new ExactCollection<>("0")));
        assertFalse(exactCollection.equals(new ExactCollection<>("0", "2")));
        assertFalse(exactCollection.equals("not-a-collection"));
    }

    @Test
    void toArrayCopiesElementsAndUsesRequestedArrayType() {
        ExactCollection<String> exactCollection = new ExactCollection<>("0", "1");

        Object[] rawArray = exactCollection.toArray();
        String[] typedArray = exactCollection.toArray(new String[0]);
        exactCollection.add(0, "changed");

        assertArrayEquals(new Object[]{"0", "1"}, rawArray);
        assertArrayEquals(new String[]{"0", "1"}, typedArray);
    }

    @Test
    void removeReturnsTrueAndClearsMatchingSlot() {
        ExactCollection<String> exactCollection = new ExactCollection<>("0", "1", "2");

        assertTrue(exactCollection.remove("1"));
        assertArrayEquals(new Object[]{"0", null, "2"}, exactCollection.toArray());
    }

    @Test
    void removeReturnsFalseWhenElementDoesNotExist() {
        ExactCollection<String> exactCollection = new ExactCollection<>("0", "1");

        assertFalse(exactCollection.remove("missing"));
        assertArrayEquals(new Object[]{"0", "1"}, exactCollection.toArray());
    }

    @Test
    void clearKeepsSizeAndClearsAllSlots() {
        ExactCollection<String> exactCollection = new ExactCollection<>("0", "1");

        exactCollection.clear();

        assertEquals(2, exactCollection.size());
        assertArrayEquals(new Object[]{null, null}, exactCollection.toArray());
    }

    @Test
    void bulkMutationsAreUnsupportedBecauseSlotsAreIndexAddressed() {
        ExactCollection<String> exactCollection = new ExactCollection<>("0", "1");

        assertThrows(UnsupportedOperationException.class, () -> exactCollection.addAll(List.of("2")));
        assertThrows(UnsupportedOperationException.class, () -> exactCollection.removeAll(List.of("1")));
        assertThrows(UnsupportedOperationException.class, () -> exactCollection.retainAll(List.of("0")));
    }
}
