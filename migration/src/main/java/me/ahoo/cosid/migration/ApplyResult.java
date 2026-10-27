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

/**
 * {@code apply-result.json}, the output of {@code apply} and the input of {@code verify} and {@code config} (09 §3; the
 * format is documented in the module README).
 *
 * @param format             {@value #FORMAT}
 * @param planSha256         the SHA-256 of the plan file it applied
 * @param namespace          the namespace
 * @param source             the 4.x store, without passwords
 * @param target             the 5.0 store, without passwords
 * @param appliedAt          {@code T_apply}, the target store clock when apply wrote
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
 */
public record ApplyResult(String format, String planSha256, String namespace, StoreLocation source, StoreLocation target, long appliedAt,
                          long sourceMaxTimestamp, long sourceSkewMillis, long w, String notBefore, long gap, boolean sealed, long sealGap,
                          long observedMillis, Plan.Slots slots, List<SegmentResult> segments, List<String> warnings) {
    public static final String FORMAT = "cosid-migration-result/1";

    /**
     * One migrated segment.
     *
     * @param sourceName the 4.x name
     * @param targetKey  the 5.0 range key
     * @param lastMaxId  the largest value 4.x reserved before the seal
     * @param next       {@code lastMaxId + 1 + gap}, the planned next value
     * @param targetNext the next value of the target after the write, at least {@code next}
     * @param sealedAt   the 4.x value after the seal; null when not sealed
     */
    public record SegmentResult(String sourceName, String targetKey, long lastMaxId, long next, long targetNext, Long sealedAt) {
    }
}
