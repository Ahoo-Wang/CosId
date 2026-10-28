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

package me.ahoo.cosid.store.zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The slot state and its derived times, without a server: the invariants of 05 §2.2 hold whatever the {@code mtime}
 * of each write, including a clock that jumps back as after a leader failover (05 §3, W).
 */
class SlotStateTest {
    private static final Owner A = new Owner("host-a", UUID.randomUUID());
    private static final Owner B = new Owner("host-b", UUID.randomUUID());

    @Test
    void takeStartsAtTheNextFloorWithTheCeilingBeyondTheExpiry() {
        SlotState a = SlotState.NEW.take(A, 0, 1_000, 100);
        SlotLease lease = a.toLease("ns", 3, 10_000, 10_000);
        assertEquals(1, lease.fence());
        assertEquals(0, lease.floor());
        assertEquals(11_000, lease.expiresAt());
        assertEquals(11_100, lease.ceiling());
        assertEquals(A, lease.owner());
        assertEquals("host-a", a.lastOwnerKey());
    }

    @Test
    void ceilingNeverDecreasesWhenTheStoreClockJumpsBack() {
        SlotState a = SlotState.NEW.take(A, 0, 1_000, 100);
        long first = a.ceiling(10_000);
        // The next write is stamped by a leader whose clock is 5s behind.
        SlotState renewed = a.renew(10_000, 1_000, 100);
        assertEquals(11_000, renewed.expiresAt(5_000), "the expiry never moves back");
        assertEquals(first, renewed.ceiling(5_000), "nor does the ceiling");
        SlotState again = renewed.renew(5_000, 1_000, 100);
        assertEquals(first, again.ceiling(4_000));
        // B takes over: its floor is the last ceiling, though the clock of its write is earlier still.
        SlotState b = again.take(B, 4_000, 1_000, 100);
        SlotLease lease = b.toLease("ns", 0, 3_000, 3_000);
        assertEquals(first, lease.floor());
        assertEquals(first, lease.ceiling(), "a ceiling below the floor is raised to it");
        assertEquals(2, lease.fence());
    }

    @Test
    void releaseKeepsTheNextFloorBetweenFloorAndCeiling() {
        SlotState a = SlotState.NEW.take(A, 0, 1_000, 100).renew(10_000, 1_000, 100);
        long ceiling = a.ceiling(10_500);
        SlotState released = a.release(10_500, 10_700);
        assertNull(released.owner());
        assertEquals(2, released.fence());
        assertEquals(10_700, released.nextFloor(20_000), "the exact last issued, whatever the clock later");
        assertEquals(ceiling, a.release(10_500, Long.MAX_VALUE).nextFloor(0));
        assertEquals(0, a.release(10_500, Long.MIN_VALUE).nextFloor(0), "nothing issued keeps the floor");
        assertTrue(released.free(10_500, 10_500));
        assertEquals(10_700, released.take(B, 10_500, 1_000, 100).floor());
    }

    @Test
    void heldByMatchesIncarnationAndFence() {
        SlotState a = SlotState.NEW.take(A, 0, 1_000, 100);
        SlotLease lease = a.toLease("ns", 0, 10_000, 10_000);
        assertTrue(a.heldBy(lease));
        assertTrue(!a.release(10_000, 0).heldBy(lease));
        SlotLease otherFence = SlotLease.builder()
            .namespace("ns")
            .slot(0)
            .owner(A)
            .fence(7)
            .expiresAt(lease.expiresAt())
            .floor(lease.floor())
            .ceiling(lease.ceiling())
            .storeNow(lease.storeNow())
            .build();
        assertTrue(!a.heldBy(otherFence));
        assertTrue(!a.free(10_000, 11_000), "expiresAt is not before now");
        assertTrue(a.free(10_000, 11_001));
    }

    @Test
    void dataRoundTripsAndKeepsOddOwnerKeys() {
        Owner odd = new Owner("pod=1\nhost|é %", UUID.randomUUID());
        SlotState state = SlotState.NEW.take(odd, 0, 30_000, 1_000).renew(5, 30_000, 1_000);
        assertEquals(state, SlotState.parse("/p", state.bytes()));
        SlotState released = state.release(7, 9);
        assertEquals(released, SlotState.parse("/p", released.bytes()));
        assertEquals(SlotState.NEW, SlotState.parse("/p", new byte[0]));
        String text = new String(state.bytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("fence=1\n") && text.contains("ttl=30000\n"), text);
        assertThrows(CosIdException.class, () -> SlotState.parse("/p", "fence=x\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(CosIdException.class, () -> SlotState.parse("/p", "owner=a\n".getBytes(StandardCharsets.UTF_8)));
    }
}
