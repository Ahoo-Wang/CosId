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

package me.ahoo.cosid.shardingsphere.sharding.interval;

import static me.ahoo.cosid.shardingsphere.sharding.mod.CosIdModShardingAlgorithm.MODULO_KEY;

import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import me.ahoo.cosid.shardingsphere.sharding.mod.CosIdModShardingAlgorithm;

import com.google.common.collect.Range;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;


/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class ModShardingAlgorithmBenchmark {

    private static final String LOGIC_TABLE_NAME = "t_mod";
    private static final String LOGIC_NAME_PREFIX = LOGIC_TABLE_NAME + "_";
    private static final String ID_COLUMN_NAME = "id";
    @Param({"10", "100", "1000", "10000", "100000"})
    private int divisor;
    private int randomBound;
    CosIdModShardingAlgorithm cosIdModShardingAlgorithm;
    org.apache.shardingsphere.sharding.algorithm.sharding.mod.ModShardingAlgorithm officeModShardingAlgorithm;

    @Setup
    public void init() {

        randomBound = divisor * 10;
        Properties properties = new Properties();

        properties.setProperty(CosIdAlgorithm.LOGIC_NAME_PREFIX_KEY, LOGIC_NAME_PREFIX);
        properties.setProperty(MODULO_KEY, String.valueOf(divisor));
        properties.setProperty("sharding-count", String.valueOf(divisor));

        cosIdModShardingAlgorithm = new CosIdModShardingAlgorithm();
        cosIdModShardingAlgorithm.setProps(properties);
        cosIdModShardingAlgorithm.init();

        officeModShardingAlgorithm = new org.apache.shardingsphere.sharding.algorithm.sharding.mod.ModShardingAlgorithm();
        officeModShardingAlgorithm.setProps(properties);
        officeModShardingAlgorithm.init();
    }

    public PreciseShardingValue<Comparable<?>> getRandomId() {
        long id = ThreadLocalRandom.current().nextLong(0, randomBound);
        return new PreciseShardingValue(LOGIC_TABLE_NAME, ID_COLUMN_NAME, id);
    }

    public RangeShardingValue<Comparable<?>> getRandomRangeId() {
        long randomLower = ThreadLocalRandom.current().nextLong(0, randomBound);
        long randomUpper = ThreadLocalRandom.current().nextLong(randomLower, randomBound);
        return new RangeShardingValue<>(LOGIC_TABLE_NAME, ID_COLUMN_NAME, Range.closed(randomLower, randomUpper));
    }

    @Benchmark
    public String cosid_precise() {
        return cosIdModShardingAlgorithm.doSharding(cosIdModShardingAlgorithm.getModCycle().getEffectiveNodes(), getRandomId());
    }

    @SuppressWarnings("unchecked")
    @Benchmark
    public Collection<String> cosid_range() {
        return cosIdModShardingAlgorithm.doSharding(cosIdModShardingAlgorithm.getModCycle().getEffectiveNodes(), getRandomRangeId());
    }

    @Benchmark
    public String office_precise() {
        return officeModShardingAlgorithm.doSharding(cosIdModShardingAlgorithm.getModCycle().getEffectiveNodes(), getRandomId());
    }

    @Benchmark
    public Collection<String> office_range() {
        return officeModShardingAlgorithm.doSharding(cosIdModShardingAlgorithm.getModCycle().getEffectiveNodes(), getRandomRangeId());
    }
}
