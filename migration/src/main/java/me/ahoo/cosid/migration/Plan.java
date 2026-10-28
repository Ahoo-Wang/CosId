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
 * @param shared        the plan of a shared {@code cosid.generator.namespace} itself (api-freeze.md §2.7); null otherwise
 * @param generatorNamespace the shared namespace the CosIdGenerator of this service uses, as the target records it;
 *                      null when the service has none
 */
public record Plan(String format, String createdAt, String namespace, StoreLocation source, StoreLocation target, StoreLocation machineSource,
                   StoreLocation machineTarget, Map<String, List<String>> extraNamespaces, Slots slots,
                   Map<String, String> renames, Map<String, String> groupPatterns, List<SegmentPlan> segments, List<String> unrecognized,
                   Machines machines, List<LayoutCheck> layouts, List<String> warnings, Shared shared, GeneratorNamespace generatorNamespace) {
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
     * @param slotOffset      the slot offset (api-freeze.md §2.1.1); 0 but in a shared namespace migrated rolling
     */
    public record Slots(int bits, int limit, long toleranceMillis, int slotOffset) {
        public static Slots of(int bits, long toleranceMillis) {
            return of(bits, toleranceMillis, 0);
        }

        public static Slots of(int bits, long toleranceMillis, int slotOffset) {
            if (bits < 1 || bits > 20) {
                throw new IllegalArgumentException("slot bits must be in [1, 20]: " + bits);
            }
            if (slotOffset < 0) {
                throw new IllegalArgumentException("the slot offset must not be negative: " + slotOffset);
            }
            return new Slots(bits, 1 << bits, toleranceMillis, slotOffset);
        }
    }

    /**
     * The plan of a shared {@code cosid.generator.namespace} (api-freeze.md §2.7): machine records only, migrated once for
     * every service that shares it.
     *
     * @param maxMachineId      M, the largest 4.x machine id ever handed out in it; -1 without records
     * @param recommendedOffset the default slot offset, {@code 2^nodeBits - 2^bits}: the 5.0 slots at the top of the nodes
     * @param nodeBits          the node bits of the 5.0 CosIdGenerator (the 4.x {@code cosid.generator.machine-bit}): the
     *                          slots end at {@code slotOffset + 2^bits <= 2^nodeBits}
     * @param rolling           a slot offset above M: 4.x keeps running while each service moves to 5.0
     * @param legacyCap         the hard cap of the 4.x distributor, {@code 2^(cosid.generator.machine-bit)}: 4.x 4.1.0 never
     *                          hands out a machine id at or above it ({@code MachineIdOverflowException})
     * @param headroom          {@code slotOffset - (M + 1)}: how many more machine ids 4.x can hand out before its nodes meet
     *                          5.0's; 0 when migrated at once
     * @param guaranteed        whether the nodes stay apart by construction, {@code slotOffset >= legacyCap}; false for a
     *                          rolling migration that keeps the 4.x bits, where only {@code verify} checks it afterwards
     */
    public record Shared(int maxMachineId, int recommendedOffset, int nodeBits, boolean rolling, long legacyCap, long headroom,
                         boolean guaranteed) {
    }

    /**
     * The shared namespace of a service's CosIdGenerator, which {@code config} writes as the named machine
     * {@code generator}.
     *
     * @param namespace       the namespace
     * @param limit           its recorded slot limit; 0 when not prepared yet
     * @param toleranceMillis its recorded tolerance
     * @param slotOffset      its recorded slot offset
     */
    public record GeneratorNamespace(String namespace, int limit, long toleranceMillis, int slotOffset) {
        /**
         * Whether the shared namespace was migrated (prepared in the target) before.
         *
         * @return true if it has metadata
         */
        public boolean prepared() {
            return limit > 0;
        }

        /**
         * The slot bits of the recorded limit.
         *
         * @return {@code log2(limit)}
         */
        public int bits() {
            return Integer.numberOfTrailingZeros(limit);
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
     * @param maxMachineId     the largest 4.x machine id handed out in the namespace, -1 without records
     */
    public record Machines(int records, long maxTimestamp, long targetNow, long sourceSkewMillis, long estimatedW, int maxMachineId) {
    }
}
