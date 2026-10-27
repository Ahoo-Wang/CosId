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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShardingRangeTest {

    @Test
    void factoriesSetTheBounds() {
        assertEquals(new ShardingRange<>(1, BoundType.CLOSED, 2, BoundType.CLOSED), ShardingRange.closed(1, 2));
        assertEquals(new ShardingRange<>(1, BoundType.OPEN, 2, BoundType.OPEN), ShardingRange.open(1, 2));
        assertEquals(new ShardingRange<>(1, BoundType.CLOSED, 2, BoundType.OPEN), ShardingRange.closedOpen(1, 2));
        assertEquals(new ShardingRange<>(1, BoundType.OPEN, 2, BoundType.CLOSED), ShardingRange.openClosed(1, 2));
        assertEquals(new ShardingRange<>(1, BoundType.CLOSED, null, null), ShardingRange.atLeast(1));
        assertEquals(new ShardingRange<>(1, BoundType.OPEN, null, null), ShardingRange.greaterThan(1));
        assertEquals(new ShardingRange<>(null, null, 2, BoundType.CLOSED), ShardingRange.atMost(2));
        assertEquals(new ShardingRange<>(null, null, 2, BoundType.OPEN), ShardingRange.lessThan(2));
        assertEquals(ShardingRange.closed(3, 3), ShardingRange.singleton(3));
        assertSame(ShardingRange.<Integer>all(), ShardingRange.<Long>all());
        assertEquals(ShardingRange.all(), new ShardingRange<Integer>(null, BoundType.CLOSED, null, BoundType.CLOSED));
    }

    @Test
    void invertedRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShardingRange.closed(2, 1));
        assertThrows(IllegalArgumentException.class, () -> ShardingRange.open(1, 1));
        assertThrows(NullPointerException.class, () -> new ShardingRange<>(1, null, 2, BoundType.OPEN));
    }

    @Test
    void endpointsOfAnUnboundedSideThrow() {
        ShardingRange<Integer> all = ShardingRange.all();
        assertFalse(all.hasLowerBound());
        assertFalse(all.hasUpperBound());
        assertThrows(IllegalStateException.class, all::lowerEndpoint);
        assertThrows(IllegalStateException.class, all::upperEndpoint);
        assertThrows(IllegalStateException.class, all::lowerBoundType);
        assertThrows(IllegalStateException.class, all::upperBoundType);
        ShardingRange<Integer> closed = ShardingRange.closedOpen(1, 2);
        assertEquals(1, closed.lowerEndpoint());
        assertEquals(BoundType.CLOSED, closed.lowerBoundType());
        assertEquals(2, closed.upperEndpoint());
        assertEquals(BoundType.OPEN, closed.upperBoundType());
    }

    @Test
    void isEmpty() {
        assertTrue(ShardingRange.closedOpen(1, 1).isEmpty());
        assertTrue(ShardingRange.openClosed(1, 1).isEmpty());
        assertFalse(ShardingRange.singleton(1).isEmpty());
        assertFalse(ShardingRange.all().isEmpty());
        assertFalse(ShardingRange.closed(1, 2).isEmpty());
    }

    @Test
    void contains() {
        assertTrue(ShardingRange.closed(1, 3).contains(1));
        assertTrue(ShardingRange.closed(1, 3).contains(3));
        assertFalse(ShardingRange.open(1, 3).contains(1));
        assertFalse(ShardingRange.open(1, 3).contains(3));
        assertTrue(ShardingRange.open(1, 3).contains(2));
        assertFalse(ShardingRange.closed(1, 3).contains(0));
        assertFalse(ShardingRange.closed(1, 3).contains(4));
        assertTrue(ShardingRange.atLeast(1).contains(Integer.MAX_VALUE));
        assertTrue(ShardingRange.lessThan(1).contains(Integer.MIN_VALUE));
        assertFalse(ShardingRange.lessThan(1).contains(1));
        assertTrue(ShardingRange.<Integer>all().contains(0));
        assertFalse(ShardingRange.closedOpen(1, 1).contains(1));
    }

    @Test
    void isConnectedAsGuava() {
        assertTrue(ShardingRange.closedOpen(1, 2).isConnected(ShardingRange.closed(2, 3)));
        assertTrue(ShardingRange.closed(1, 2).isConnected(ShardingRange.openClosed(2, 3)));
        assertFalse(ShardingRange.closedOpen(1, 2).isConnected(ShardingRange.openClosed(2, 3)));
        assertFalse(ShardingRange.closed(1, 2).isConnected(ShardingRange.closed(3, 4)));
        assertFalse(ShardingRange.closed(3, 4).isConnected(ShardingRange.closed(1, 2)));
        assertTrue(ShardingRange.closed(1, 5).isConnected(ShardingRange.closed(2, 3)));
        assertTrue(ShardingRange.<Integer>all().isConnected(ShardingRange.closed(2, 3)));
        assertTrue(ShardingRange.atMost(2).isConnected(ShardingRange.atLeast(2)));
        assertFalse(ShardingRange.lessThan(2).isConnected(ShardingRange.greaterThan(2)));
    }

    @Test
    void toStringLooksLikeGuava() {
        assertEquals("[1..2)", ShardingRange.closedOpen(1, 2).toString());
        assertEquals("(1..2]", ShardingRange.openClosed(1, 2).toString());
        assertEquals("(-∞..+∞)", ShardingRange.all().toString());
        assertEquals("[1..+∞)", ShardingRange.atLeast(1).toString());
        assertNotEquals(ShardingRange.closed(1, 2), ShardingRange.closedOpen(1, 2));
    }
}
