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

package me.ahoo.cosid.lease;

import me.ahoo.cosid.observe.GeneratorListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the lease events as {@code name:detail} strings.
 */
final class RecordingListener implements GeneratorListener {
    final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void onSlotAcquired(SlotLease lease, String reason) {
        events.add("acquired:" + lease.slot() + ":" + reason);
    }

    @Override
    public void onSlotRenewed(SlotLease lease) {
        events.add("renewed:" + lease.slot());
    }

    @Override
    public void onSlotRenewFailed(SlotLease lease, Throwable error) {
        events.add("renewFailed:" + lease.slot());
    }

    @Override
    public void onSlotLost(SlotLease lease) {
        events.add("lost:" + lease.slot());
    }

    @Override
    public void onSlotReleased(SlotLease lease) {
        events.add("released:" + lease.slot());
    }

    @Override
    public void onClockSkew(String namespace, long skewMillis) {
        events.add("skew:" + skewMillis);
    }

    @Override
    public void onDuplicateOwnerKey(String namespace, String ownerKey) {
        events.add("duplicate:" + ownerKey);
    }

    long count(String prefix) {
        return events.stream().filter(event -> event.startsWith(prefix)).count();
    }
}
