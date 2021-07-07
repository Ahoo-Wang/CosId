package me.ahoo.cosid;

import me.ahoo.cosid.snowflake.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SnowflakeIdBenchmark {
    SnowflakeId millisecondSnowflakeId;
    SnowflakeId secondSnowflakeId;
    SnowflakeId safeJsMillisecondSnowflakeId;
    SnowflakeId safeJsSecondSnowflakeId;
    SnowflakeFriendlyId snowflakeFriendlyId;

    @Setup
    public void setup() {
        millisecondSnowflakeId = new ClockSyncSnowflakeId(new MillisecondSnowflakeId(1));
        secondSnowflakeId = new ClockSyncSnowflakeId(new SecondSnowflakeId(1));
        safeJsSecondSnowflakeId = new ClockSyncSnowflakeId(SafeJavaScriptSnowflakeId.ofSecond(1));
        safeJsMillisecondSnowflakeId = new ClockSyncSnowflakeId(SafeJavaScriptSnowflakeId.ofMillisecond(1));
        snowflakeFriendlyId = new DefaultSnowflakeFriendlyId(new ClockSyncSnowflakeId(new MillisecondSnowflakeId(1), ClockBackwardsSynchronizer.DEFAULT));
    }

    @Benchmark
    public long millisecondSnowflakeId_generate() {
        return millisecondSnowflakeId.generate();
    }

    @Benchmark
    public SnowflakeIdState millisecondSnowflakeId_friendlyId() {
        return snowflakeFriendlyId.friendlyId();
    }

    @Benchmark
    public long secondSnowflakeId_generate() {
        return secondSnowflakeId.generate();
    }

    @Benchmark
    public long safeJsMillisecondSnowflakeId_generate() {
        return safeJsMillisecondSnowflakeId.generate();
    }

    @Benchmark
    public long safeJsSecondSnowflakeId_generate() {
        return safeJsSecondSnowflakeId.generate();
    }
}
