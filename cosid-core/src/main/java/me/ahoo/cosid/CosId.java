/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosid;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @author ahoo wang
 */
public final class CosId {
    public static final String COSID = "cosid";
    public static final String COSID_PREFIX = COSID + ".";
    public static final LocalDateTime PIGEON_EPOCH_DATE;
    /**
     *  1577203200000
     */
    public static final long COSID_EPOCH;
    /**
     *  1577203200
     */
    public static final long COSID_EPOCH_SECOND;

    static {
        PIGEON_EPOCH_DATE = LocalDateTime.of(2019, 12, 24, 16, 0);
        COSID_EPOCH = PIGEON_EPOCH_DATE.toInstant(ZoneOffset.UTC).toEpochMilli();
        COSID_EPOCH_SECOND = COSID_EPOCH / 1000;
    }
}
