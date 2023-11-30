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

package me.ahoo.cosid.test.mod;

import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;

import com.netease.nim.camellia.id.gen.snowflake.CamelliaSnowflakeConfig;
import com.netease.nim.camellia.id.gen.snowflake.CamelliaSnowflakeIdGen;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * SnowflakeId Benchmark.
 *
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SnowflakeIdBenchmark {
    public static final int TEST_MACHINE_ID = 1;
    SnowflakeFriendlyId cosidSnowflakeId;
    CamelliaSnowflakeIdGen camelliaSnowflakeId;
    
    /**
     * Initialize IdGenerator.
     */
    @Setup
    public void setup() {
        MillisecondSnowflakeId idGen = new MillisecondSnowflakeId(TEST_MACHINE_ID);
        cosidSnowflakeId = new DefaultSnowflakeFriendlyId(new ClockSyncSnowflakeId(idGen));
        CamelliaSnowflakeConfig camelliaSnowflakeCfg = new CamelliaSnowflakeConfig();
        camelliaSnowflakeCfg.setWorkerIdGen(maxWorkerId -> TEST_MACHINE_ID);
        camelliaSnowflakeId = new CamelliaSnowflakeIdGen(camelliaSnowflakeCfg);
    }
    
    @Benchmark
    public long cosid() {
        return cosidSnowflakeId.generate();
    }
    
    @Benchmark
    public long netease() {
        return camelliaSnowflakeId.genId();
    }
}
