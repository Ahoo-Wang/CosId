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

package me.ahoo.cosid;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.string.CosIdGenerator;
import me.ahoo.cosid.string.CosIdState;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.UUID;

/**
 * SegmentId Benchmark.
 *
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class CosIdGeneratorBenchmark {
    AtomicLongGenerator atomicLongGenerator;
    CosIdGenerator cosIdGenerator;
    
    /**
     * Initialize IdGenerator.
     */
    @Setup
    public void setup() {
        atomicLongGenerator = new AtomicLongGenerator();
        cosIdGenerator = new CosIdGenerator(CosIdGenerator.DEFAULT_TIMESTAMP_BIT, CosIdGenerator.DEFAULT_MACHINE_BIT, CosIdGenerator.DEFAULT_SEQUENCE_BIT, 1);
    }
    
    @Benchmark
    public UUID uuid_generate() {
        return UUID.randomUUID();
    }
    
    @Benchmark
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    @Benchmark
    public long nanoTime() {
        return System.nanoTime();
    }
    
    @Benchmark
    public long atomicLong_generate() {
        return atomicLongGenerator.generate();
    }
    
    @Benchmark
    public String atomicLong_generateAsString() {
        return Radix62IdConverter.PAD_START.asString(atomicLongGenerator.generate());
    }
    
    @Benchmark
    public String cosIdGenerator_generateAsString() {
        try {
            return cosIdGenerator.generateAsString();
        } catch (ClockBackwardsException exception) {
            ClockBackwardsSynchronizer.DEFAULT.syncUninterruptibly(cosIdGenerator.getLastTimestamp());
            return cosIdGenerator.generateAsString();
        }
    }
    
    @Benchmark
    public CosIdState cosIdGenerator_generateAsState() {
        try {
            return cosIdGenerator.generateAsState();
        } catch (ClockBackwardsException exception) {
            ClockBackwardsSynchronizer.DEFAULT.syncUninterruptibly(cosIdGenerator.getLastTimestamp());
            return cosIdGenerator.generateAsState();
        }
    }
    
}
