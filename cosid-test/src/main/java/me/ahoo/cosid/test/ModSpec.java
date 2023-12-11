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

package me.ahoo.cosid.test;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

@Slf4j
public class ModSpec implements Runnable, TestSpec {
    public static final Runnable DEFAULT_WAIT = () -> {
        int wait = ThreadLocalRandom.current().nextInt(0, 1000);
        LockSupport.parkNanos(wait);
    };
    /**
     * 测试迭代次数.
     */
    private final int iterations;
    /**
     * 取模除数.
     */
    private final int divisor;
    /**
     * 允许的标准差.
     */
    private final double allowablePopStd;

    private final LongSupplier idGenerator;
    /**
     * 预期平均命中数.
     */
    private final int expectedAvgHits;
    private final Runnable wait;
    private final int[] hits;
    private double popVariance;
    private double popStd;
    private double popStdError;

    public ModSpec(int iterations, int divisor, double allowablePopStd, LongSupplier idGenerator, Runnable wait) {
        this.iterations = iterations;
        this.divisor = divisor;
        this.allowablePopStd = allowablePopStd;
        this.idGenerator = idGenerator;
        this.wait = wait;
        expectedAvgHits = iterations / divisor;
        hits = new int[divisor];

    }

    @Override
    public void run() {
        if (hits[0] > 0) {
            return;
        }
        for (int i = 0; i < iterations; i++) {
            long id = idGenerator.getAsLong();
            int mod = (int) (id % divisor);
            hits[mod]++;
            wait.run();
        }

        popVariance = Arrays.stream(hits)
            .map(hit -> hit - expectedAvgHits)
            .mapToDouble(diff -> Math.pow(diff, 2))
            .sum() / hits.length;
        /**
         * 标准方差
         */
        popStd = Math.sqrt(popVariance);
        /**
         * 标准误差
         */
        popStdError = popStd / Math.sqrt(hits.length);
        if (log.isInfoEnabled()) {
            log.info("Report - iterations:{},divisor:{},allowablePopStd:{},expectedAvgHits:{},popStd:{},popStdError:{} - hits:{}",
                iterations, divisor, allowablePopStd, expectedAvgHits, popStd, popStdError, hits);
        }
    }

    @Override
    public void verify() {
        run();
        if (popStd > allowablePopStd) {
            throw new AssertionError("popStd:" + popStd + ",allowablePopStd:" + allowablePopStd);
        }
    }

}
