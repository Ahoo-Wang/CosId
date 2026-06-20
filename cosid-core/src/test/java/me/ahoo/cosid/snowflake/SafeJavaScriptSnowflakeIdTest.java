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

package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.CosId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SafeJavaScriptSnowflakeId}.
 *
 * @author ahoo wang
 */
class SafeJavaScriptSnowflakeIdTest {

    /**
     * The default millisecond safe-JS factory must build a {@link MillisecondSnowflakeId}
     * whose epoch is in <em>milliseconds</em>, because the generator's
     * {@code getCurrentTime()} returns {@link System#currentTimeMillis()}.
     *
     * <p>Passing {@link CosId#COSID_EPOCH_SECOND} (seconds) here is a unit mismatch
     * that inflates {@code diffTimestamp = currentTimeMs - epoch} by ~1000x,
     * exhausting the 41-bit timestamp field in ~13 years instead of ~69 years and
     * corrupting the wall-clock time returned by the state parser.
     */
    @Test
    void ofMillisecondUsesMillisecondEpoch() {
        MillisecondSnowflakeId snowflakeId = SafeJavaScriptSnowflakeId.ofMillisecond(1);
        Assertions.assertEquals(CosId.COSID_EPOCH, snowflakeId.getEpoch(),
            "ofMillisecond must use the millisecond epoch (COSID_EPOCH), not COSID_EPOCH_SECOND.");
    }

    /**
     * A freshly constructed default safe-JS millisecond generator must still have
     * the bulk of its 41-bit timestamp budget remaining (i.e. it must not be near
     * overflow today). The buggy seconds-epoch implementation would have already
     * burned ~93% of the budget by 2026.
     */
    @Test
    void ofMillisecondHasHealthyTimestampBudget() {
        MillisecondSnowflakeId snowflakeId = SafeJavaScriptSnowflakeId.ofMillisecond(1);
        long diffTimestamp = System.currentTimeMillis() - snowflakeId.getEpoch();
        long remaining = snowflakeId.getMaxTimestamp() - diffTimestamp;
        // With the correct epoch, >50% of the budget should remain. The buggy
        // (seconds) epoch leaves only ~19% and is shrinking fast toward 2039 overflow.
        Assertions.assertTrue(remaining > snowflakeId.getMaxTimestamp() / 2,
            "Millisecond safe-JS snowflake should have most of its timestamp budget left. " +
                "remaining=" + remaining + " maxTimestamp=" + snowflakeId.getMaxTimestamp());
    }

    /**
     * The second-precision safe-JS factory legitimately uses {@link CosId#COSID_EPOCH_SECOND},
     * since {@link SecondSnowflakeId#getCurrentTime()} works in seconds. This guards
     * against an over-broad fix that changes both factories.
     */
    @Test
    void ofSecondUsesSecondEpoch() {
        SecondSnowflakeId snowflakeId = SafeJavaScriptSnowflakeId.ofSecond(1);
        Assertions.assertEquals(CosId.COSID_EPOCH_SECOND, snowflakeId.getEpoch());
    }
}
