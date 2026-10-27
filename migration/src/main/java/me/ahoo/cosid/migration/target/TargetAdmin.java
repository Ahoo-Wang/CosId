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

package me.ahoo.cosid.migration.target;

import java.util.List;
import java.util.Optional;

/**
 * The narrow admin path of the migration into one 5.0 store (09 §3): it writes the 5.0 schema of {@code store/*}
 * directly, and only ever raises {@code next} and {@code next_floor} ({@code max(current, planned)}), so that running it
 * again changes nothing. It is not a {@code RangeStore} or {@code SlotLeaseStore}: those have no way to set a value.
 */
public interface TargetAdmin extends AutoCloseable {
    /**
     * The store clock (06 §1), UTC epoch milliseconds.
     *
     * @return the time
     */
    long now();

    /**
     * Reads a range key.
     *
     * @param key the range key
     * @return its state, empty if it does not exist
     */
    Optional<RangeState> range(String key);

    /**
     * Raises a range key to at least {@code next}, creating it unsealed when it does not exist. A sealed key keeps its
     * seal.
     *
     * @param key  the range key
     * @param next the planned next value
     * @return the state after the write
     */
    RangeState raise(String key, long next);

    /**
     * The range keys starting with a prefix.
     *
     * @param prefix the prefix
     * @return the keys, in key order
     */
    List<RangeState> ranges(String prefix);

    /**
     * Deletes a sealed range key (the {@code purge} command, D23: offline only).
     *
     * @param key the range key
     * @return true if a sealed key was deleted
     */
    boolean delete(String key);

    /**
     * Reads the slot metadata and slots of a namespace.
     *
     * @param namespace the namespace
     * @return the state, empty when the namespace has no metadata yet
     */
    Optional<NamespaceState> namespace(String namespace);

    /**
     * Prepares the slots of a namespace (09 §4.2): records {@code {limit, tolerance}} when there is no metadata yet,
     * creates the missing slots {@code [0, limit)} free, and raises every slot's next floor to at least
     * {@code nextFloor}.
     *
     * @param namespace       the namespace
     * @param limit           the slot limit
     * @param toleranceMillis the tolerance
     * @param nextFloor       W
     * @return the state after the write
     * @throws IllegalStateException if the metadata differs, or a slot has a live lease: 5.0 runs already
     */
    NamespaceState prepare(String namespace, int limit, long toleranceMillis, long nextFloor);

    @Override
    void close();

    /**
     * The error of a namespace recorded with other parameters.
     */
    static IllegalStateException mismatch(String namespace, int limit, long toleranceMillis, int recordedLimit, long recordedTolerance) {
        return new IllegalStateException("Namespace [" + namespace + "] is recorded with limit " + recordedLimit + " and tolerance " + recordedTolerance
            + "ms, the plan has " + limit + " and " + toleranceMillis + "ms: use --slot-bits / --tolerance of the 5.0 configuration");
    }

    /**
     * The error of a namespace with live leases.
     */
    static IllegalStateException live(String namespace, int live) {
        return new IllegalStateException("Namespace [" + namespace + "] has " + live + " live slot lease(s) in the target: 5.0 must not run during the migration");
    }
}
