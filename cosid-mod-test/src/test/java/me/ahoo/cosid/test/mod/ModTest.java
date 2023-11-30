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
import me.ahoo.cosid.test.ModSpec;

import com.netease.nim.camellia.id.gen.snowflake.CamelliaSnowflakeConfig;
import com.netease.nim.camellia.id.gen.snowflake.CamelliaSnowflakeIdGen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public class ModTest {
    public static final int TEST_MACHINE_ID = 1;
    private static final int ITERATIONS = 1000;
    private static final double ALLOWABLE_POP_STD = 10;
    SnowflakeFriendlyId cosidSnowflakeId;
    CamelliaSnowflakeIdGen camelliaSnowflakeId;
    
    @BeforeEach
    void setup() {
        MillisecondSnowflakeId idGen = new MillisecondSnowflakeId(TEST_MACHINE_ID);
        cosidSnowflakeId = new DefaultSnowflakeFriendlyId(new ClockSyncSnowflakeId(idGen));
        CamelliaSnowflakeConfig camelliaSnowflakeCfg = new CamelliaSnowflakeConfig();
        camelliaSnowflakeCfg.setWorkerIdGen(maxWorkerId -> TEST_MACHINE_ID);
        camelliaSnowflakeId = new CamelliaSnowflakeIdGen(camelliaSnowflakeCfg);
    }
    
    @Test
    public void cosidMod4() {
        ModSpec spec = new ModSpec(ITERATIONS, 4, ALLOWABLE_POP_STD, cosidSnowflakeId::generate, () -> LockSupport.parkNanos(Duration.ofMillis(1).toNanos()));
        spec.run();
    }
    
    @Test
    public void neteaseMod4() {
        ModSpec spec = new ModSpec(ITERATIONS, 4, ALLOWABLE_POP_STD, camelliaSnowflakeId::genId, () -> LockSupport.parkNanos(Duration.ofMillis(1).toNanos()));
        spec.run();
    }
    
    @Test
    public void cosidMod128() {
        ModSpec spec = new ModSpec(ITERATIONS, 128, ALLOWABLE_POP_STD, cosidSnowflakeId::generate, () -> LockSupport.parkNanos(Duration.ofMillis(1).toNanos()));
        spec.run();
    }
    
    @Test
    public void neteaseMod128() {
        ModSpec spec = new ModSpec(ITERATIONS, 128, ALLOWABLE_POP_STD, camelliaSnowflakeId::genId, () -> LockSupport.parkNanos(Duration.ofMillis(1).toNanos()));
        spec.run();
    }
}
