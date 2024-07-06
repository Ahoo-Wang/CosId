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

package me.ahoo.cosid.segment.grouped.date;

import me.ahoo.cosid.segment.grouped.GroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupedKey;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

public abstract class AbstractDateGroupBySupplier<D extends TemporalAccessor> implements GroupBySupplier {
    protected final DateTimeFormatter formatter;

    public AbstractDateGroupBySupplier(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    abstract D now();

    abstract LocalDateTime lastTimestamp(D date);

    @Override
    public GroupedKey get() {
        D nowDate = now();
        String key = formatter.format(nowDate);

        LocalDateTime lastTs = lastTimestamp(nowDate);
        ZoneId currentZone = ZoneId.systemDefault();
        long ttlAt = lastTs.atZone(currentZone).toInstant().toEpochMilli() / 1000;
        return new GroupedKey(key, ttlAt);
    }
}
