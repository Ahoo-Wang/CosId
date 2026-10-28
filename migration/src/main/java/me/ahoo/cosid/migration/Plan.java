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

package me.ahoo.cosid.migration;

import java.util.List;
import java.util.Map;

/**
 * {@code plan.json}, the output of {@code plan} and the input of {@code apply} and {@code verify} (09 §3; the format is
 * documented in the module README).
 *
 * @param format        {@value #FORMAT}
 * @param createdAt     when the plan was made, ISO-8601
 * @param namespace     the namespace, the same in 4.x and 5.0
 * @param source        the 4.x store, without passwords
 * @param target        the 5.0 store, without passwords
 * @param machineSource where 4.x keeps its machine ids when not in {@code source} ({@code --machine-from}); null otherwise
 * @param machineTarget where the 5.0 slots go when not in {@code target} ({@code --machine-to}); null otherwise
 * @param extraNamespaces 4.x namespaces of definitions with a namespace of their own, to the 4.x segment names taken
 *                      from them; their keys {@code {ownNs}.{name}} map to {@code {namespace}/{name}}
 * @param slots         the slot parameters the 5.0 namespace gets
 * @param renames       4.x generator name to 5.0 generator name ({@code __share__ -> default} unless given otherwise)
 * @param groupPatterns 5.0 generator name to the 4.x date pattern of its group keys, where not a 5.0 preset
 * @param segments      every segment key and its mapping
 * @param unrecognized  keys under the namespace that are not migrated, with the reason
 * @param machines      the machine records and the W estimate
 * @param layouts       the layout validation (M3)
 * @param warnings      everything else the operator should read
 */
public record Plan(String format, String createdAt, String namespace, StoreLocation source, StoreLocation target, StoreLocation machineSource,
                   StoreLocation machineTarget, Map<String, List<String>> extraNamespaces, Slots slots,
                   Map<String, String> renames, Map<String, String> groupPatterns, List<SegmentPlan> segments, List<String> unrecognized,
                   Machines machines, List<LayoutCheck> layouts, List<String> warnings) {
    public static final String FORMAT = "cosid-migration-plan/1";

    /**
     * Whether every layout check passed.
     *
     * @return true if M3 holds
     */
    public boolean layoutsOk() {
        return layouts.stream().allMatch(LayoutCheck::ok);
    }

    /**
     * The slot parameters of the 5.0 namespace (05 §5).
     *
     * @param bits            the slot bits
     * @param limit           {@code 2^bits}
     * @param toleranceMillis the tolerance
     */
    public record Slots(int bits, int limit, long toleranceMillis) {
        public static Slots of(int bits, long toleranceMillis) {
            if (bits < 1 || bits > 20) {
                throw new IllegalArgumentException("slot bits must be in [1, 20]: " + bits);
            }
            return new Slots(bits, 1 << bits, toleranceMillis);
        }
    }

    /**
     * One 4.x segment key and its 5.0 key.
     *
     * @param sourceNamespace the 4.x namespace of the key
     * @param sourceName the 4.x name after {@code {sourceNamespace}.}, with {@code @groupKey} for grouped segments
     * @param generator  the 5.0 generator name
     * @param period     the 5.0 period preset of a grouped segment, null otherwise
     * @param targetKey  the 5.0 range key
     * @param lastMaxId  the largest value 4.x reserved, when planned
     * @param next       {@code lastMaxId + 1}, the planned next value before {@code --gap}
     * @param targetNext the next value the target already has, null if the key does not exist there
     */
    public record SegmentPlan(String sourceNamespace, String sourceName, String generator, String period, String targetKey, long lastMaxId, long next, Long targetNext) {
    }

    /**
     * The machine records of the namespace and the W estimate (09 §4.2).
     *
     * @param records          how many records there are
     * @param maxTimestamp     T4
     * @param targetNow        the target store clock when planned
     * @param sourceSkewMillis the source skew of the estimate
     * @param estimatedW       {@code max(targetNow, T4 + 1s) + sourceSkew}; apply recomputes it
     */
    public record Machines(int records, long maxTimestamp, long targetNow, long sourceSkewMillis, long estimatedW) {
    }
}
