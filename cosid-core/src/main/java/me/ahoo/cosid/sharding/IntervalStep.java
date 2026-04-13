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

import com.google.errorprone.annotations.Immutable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Represents a time interval step for sharding operations.
 *
 * <p>Defines the granularity and size of time-based sharding intervals.
 * Used for interval sharding algorithms in ShardingSphere integration.
 *
 * @author ahoo wang
 */
@Immutable
public class IntervalStep {
    /**
     * Default amount of 1.
     */
    public static final int DEFAULT_AMOUNT = 1;

    private final ChronoUnit unit;
    private final int amount;

    /**
     * Creates an interval step.
     *
     * @param unit   the time unit (years, months, days, etc.)
     * @param amount the number of units per step
     */
    public IntervalStep(ChronoUnit unit, int amount) {
        this.unit = unit;
        this.amount = amount;
    }

    /**
     * Gets the time unit.
     *
     * @return the unit
     */
    public ChronoUnit getUnit() {
        return unit;
    }

    /**
     * Gets the amount.
     *
     * @return the amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Calculates the next time by adding the interval.
     *
     * @param previous the previous time
     * @return the next time
     */
    public LocalDateTime next(LocalDateTime previous) {
        return previous.plus(amount, unit);
    }

    /**
     * Truncates time to the precision of the unit.
     *
     * @param time the time to truncate
     * @return time truncated to unit precision
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
     * Calculates the offset from start to time in unit increments.
     *
     * @param start the start time
     * @param time  the target time
     * @return the offset in units
     */
    public int offsetUnit(LocalDateTime start, LocalDateTime time) {
        return (int) (start.until(time, unit) / amount);
    }

    /**
     * Creates an interval step with default amount of 1.
     *
     * @param unit the time unit
     * @return the interval step
     */
    public static IntervalStep of(ChronoUnit unit) {
        return new IntervalStep(unit, DEFAULT_AMOUNT);
    }

    /**
     * Creates an interval step with custom amount.
     *
     * @param unit   the time unit
     * @param amount the number of units
     * @return the interval step
     */
    public static IntervalStep of(ChronoUnit unit, int amount) {
        return new IntervalStep(unit, amount);
    }
}
