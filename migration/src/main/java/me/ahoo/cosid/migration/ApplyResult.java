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
 * {@code apply-result.json}, the output of {@code apply} and the input of {@code verify} and {@code config} (09 §3; the
 * format is documented in the module README).
 *
 * @param format             {@value #FORMAT}
 * @param planSha256         the SHA-256 of the plan file it applied
 * @param namespace          the namespace
 * @param source             the 4.x store, without passwords
 * @param target             the 5.0 store, without passwords
 * @param machineTarget      where the slots went when not in {@code target}; null otherwise
 * @param renames            the 4.x to 5.0 generator names of the plan: {@code config} names the generators the same way
 * @param allowedUnmigrated  4.x keys not migrated on purpose ({@code --allow-unmigrated})
 * @param appliedAt          {@code T_apply}, the later of the target and the source store clock when apply wrote
 * @param sourceMaxTimestamp T4
 * @param sourceSkewMillis   the source skew
 * @param w                  {@code W = max(T_apply, T4 + 1s) + sourceSkew}, the next floor of every slot
 * @param notBefore          W as ISO-8601: {@code cosid.machine.static.not-before}
 * @param gap                the gap added to every segment
 * @param sealed             whether the source was sealed
 * @param sealGap            the seal gap
 * @param observedMillis     how long the quiet-period check watched the source
 * @param slots              the slot parameters
 * @param segments           every migrated segment
 * @param warnings           everything else the operator should read
 * @param shared             the plan's {@link Plan#shared()}: this result prepared a shared namespace; null otherwise
 * @param generatorNamespace the shared namespace of the service's CosIdGenerator as apply found it prepared, for
 *                           {@code config} ({@code cosid.machines.generator}); null when the service has none
 * @param machineNamespaces  every namespace of its own of the service's time-based generators as apply found it
 *                           prepared, by machine name, for {@code config} ({@code cosid.machines.<machine>}); null in
 *                           results of 5.0.0-rc.1
 */
public record ApplyResult(String format, String planSha256, String namespace, StoreLocation source, StoreLocation target, StoreLocation machineTarget,
                          Map<String, String> renames, List<String> allowedUnmigrated, long appliedAt,
                          long sourceMaxTimestamp, long sourceSkewMillis, long w, String notBefore, long gap, boolean sealed, long sealGap,
                          long observedMillis, Plan.Slots slots, List<SegmentResult> segments, List<String> warnings, Plan.Shared shared,
                          Plan.GeneratorNamespace generatorNamespace, Map<String, Plan.GeneratorNamespace> machineNamespaces) {
    public static final String FORMAT = "cosid-migration-result/1";

    /**
     * One migrated segment.
     *
     * @param sourceNamespace the 4.x namespace of the key
     * @param sourceName the 4.x name
     * @param targetKey  the 5.0 range key
     * @param lastMaxId  the largest value 4.x reserved before the seal
     * @param next       {@code lastMaxId + 1 + gap}, the planned next value
     * @param targetNext the next value of the target after the write, at least {@code next}
     * @param sealedAt   the 4.x value after the seal; null when not sealed
     */
    public record SegmentResult(String sourceNamespace, String sourceName, String targetKey, long lastMaxId, long next, long targetNext, Long sealedAt) {
    }
}
