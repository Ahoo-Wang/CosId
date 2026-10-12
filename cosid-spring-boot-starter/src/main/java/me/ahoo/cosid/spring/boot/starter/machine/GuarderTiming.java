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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.spring.boot.starter.store.StoreTypes;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Checks that the guarder renews a lease well before it expires: the first renewal comes after {@code initial-delay},
 * the next ones every {@code delay}, each may take up to the store's timeout, and the lease must not have expired by
 * then. The holder judges it with a margin for clock skew of {@code min(10s, safe-guard-duration / 10)}.
 */
final class GuarderTiming {
    private static final String GUARDER = MachineProperties.PREFIX + ".guarder.";
    
    private GuarderTiming() {
    }
    
    /**
     * The problem with the guarder timing, or {@code null} when there is none.
     *
     * @param storeTimeout the longest a renewal may take, {@link Duration#ZERO} when unknown
     */
    @Nullable
    static String check(MachineProperties.Guarder guarder, Duration storeTimeout) {
        Duration safeGuard = guarder.getSafeGuardDuration();
        Duration delay = guarder.getDelay();
        Duration initialDelay = guarder.getInitialDelay();
        if (safeGuard == null || safeGuard.isZero() || safeGuard.isNegative()) {
            return "[" + GUARDER + "safe-guard-duration] must be positive, but was [" + safeGuard + "].";
        }
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return "[" + GUARDER + "delay] must be positive, but was [" + delay + "].";
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            return "[" + GUARDER + "initial-delay] must not be negative, but was [" + initialDelay + "].";
        }
        Duration margin = margin(safeGuard);
        Duration longestWait = (delay.compareTo(initialDelay) >= 0 ? delay : initialDelay).plus(storeTimeout);
        Duration validity = safeGuard.minus(margin);
        if (longestWait.compareTo(validity) >= 0) {
            return "The guarder may renew the machine id lease too late: max([" + GUARDER + "delay]=" + delay + ", [" + GUARDER
                + "initial-delay]=" + initialDelay + ") + store timeout " + storeTimeout + " must be shorter than ["
                + GUARDER + "safe-guard-duration]=" + safeGuard + " - " + margin + " margin. Lower delay and initial-delay "
                + "below " + validity.minus(storeTimeout) + " (a third of it leaves room for missed renewals), or raise "
                + "safe-guard-duration above " + longestWait.plus(MachineIdLease.MAX_CLOCK_SKEW_MARGIN) + ".";
        }
        return null;
    }
    
    static Duration margin(Duration safeGuard) {
        Duration tenth = safeGuard.dividedBy(10);
        return tenth.compareTo(MachineIdLease.MAX_CLOCK_SKEW_MARGIN) < 0 ? tenth : MachineIdLease.MAX_CLOCK_SKEW_MARGIN;
    }
    
    /**
     * How long one renewal may take on the configured store.
     */
    static Duration storeTimeout(MachineProperties.Distributor distributor, Duration proxyReadTimeout) {
        String type = distributor.getType();
        Duration timeout = null;
        if (StoreTypes.same(type, MachineProperties.Distributor.REDIS) && distributor.getRedis() != null) {
            timeout = distributor.getRedis().getTimeout();
        } else if (StoreTypes.same(type, MachineProperties.Distributor.ZOOKEEPER) && distributor.getZookeeper() != null) {
            timeout = distributor.getZookeeper().getTimeout();
        } else if (StoreTypes.same(type, MachineProperties.Distributor.MONGO) && distributor.getMongo() != null) {
            timeout = distributor.getMongo().getTimeout();
        } else if (StoreTypes.same(type, MachineProperties.Distributor.JDBC) && distributor.getJdbc() != null) {
            timeout = distributor.getJdbc().getQueryTimeout();
        } else if (StoreTypes.same(type, MachineProperties.Distributor.PROXY)) {
            timeout = proxyReadTimeout;
        }
        return timeout == null || timeout.isNegative() ? Duration.ZERO : timeout;
    }
}
