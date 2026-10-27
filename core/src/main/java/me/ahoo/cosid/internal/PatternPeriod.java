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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.segment.Period;

import java.time.Instant;
import java.time.ZoneId;

/**
 * The preset periods.
 */
public enum PatternPeriod implements Period {
    YEAR("yyyy"),
    YEAR_MONTH("yyyyMM"),
    YEAR_MONTH_DAY("yyyyMMdd");

    private final String pattern;

    PatternPeriod(String pattern) {
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }

    @Override
    public String keyOf(Instant instant, ZoneId zone) {
        throw new UnsupportedOperationException("R1-B");
    }

    @Override
    public Instant endOf(String key, ZoneId zone) {
        throw new UnsupportedOperationException("R1-B");
    }
}
