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

package me.ahoo.cosid.sharding;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @author ahoo wang
 */
public class IntervalStep {
    public static final int DEFAULT_AMOUNT = 1;

    private final ChronoUnit unit;
    private final int amount;

    public IntervalStep(ChronoUnit unit, int amount) {
        this.unit = unit;
        this.amount = amount;
    }

    public ChronoUnit getUnit() {
        return unit;
    }

    public int getAmount() {
        return amount;
    }

    public LocalDateTime next(LocalDateTime previous) {
        return previous.plus(amount, unit);
    }

    /**
     * 按照 {@link #unit} 保留单位时间精度
     *
     * @param time
     * @return
     */
    public LocalDateTime floorUnit(LocalDateTime time) {
        switch (unit) {
            case YEARS: {
                return LocalDateTime.of(time.getYear(), 1, 1, 0, 0);
            }
            case MONTHS: {
                return LocalDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0);
            }
            case DAYS: {
                return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), 0, 0);
            }
            case HOURS: {
                return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), 0);
            }
            case MINUTES: {
                return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute());
            }
            case SECONDS: {
                return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute(), time.getSecond());
            }
            default:
                throw new IllegalStateException("Unexpected value: " + unit);
        }
    }

    /**
     * 计算单位偏移量
     * Start with 0
     *
     * @param start 最小值
     * @param time
     * @return
     */
    public int offsetUnit(LocalDateTime start, LocalDateTime time) {
        return getDiffUnit(start, time) / amount;
    }

    private int getDiffUnit(LocalDateTime startInterval, LocalDateTime time) {
        switch (unit) {
            case YEARS: {
                return getDiffYear(startInterval, time);
            }
            case MONTHS: {
                return getDiffYearMonth(startInterval, time);
            }
            case DAYS: {
                return getDiffYearMonthDay(startInterval, time);
            }
            case HOURS: {
                return getDiffYearMonthDay(startInterval, time) * 24;
            }
            case MINUTES: {
                return getDiffYearMonthDay(startInterval, time) * 24 * 60;
            }
            case SECONDS: {
                return getDiffYearMonthDay(startInterval, time) * 24 * 60 * 60;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + unit);
        }
    }

    private int getDiffYearMonthDay(LocalDateTime startInterval, LocalDateTime time) {
        return (int) (time.toLocalDate().toEpochDay() - startInterval.toLocalDate().toEpochDay());
    }

    private int getDiffYearMonth(LocalDateTime startInterval, LocalDateTime time) {
        return getDiffYear(startInterval, time) * 12 + (time.getMonthValue() - startInterval.getMonthValue());
    }

    private int getDiffYear(LocalDateTime startInterval, LocalDateTime time) {
        return time.getYear() - startInterval.getYear();
    }

    public static IntervalStep of(ChronoUnit unit) {
        return new IntervalStep(unit, DEFAULT_AMOUNT);
    }

    public static IntervalStep of(ChronoUnit unit, int amount) {
        return new IntervalStep(unit, amount);
    }
}
