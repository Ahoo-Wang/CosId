package me.ahoo.cosid;

import me.ahoo.cosid.snowflake.*;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SnowflakeIdBenchmark {
    MillisecondSnowflakeId millisecondSnowflakeId;
    SecondSnowflakeId secondSnowflakeId;
    MillisecondSnowflakeId safeJsMillisecondSnowflakeId;
    SecondSnowflakeId safeJsSecondSnowflakeId;
    SnowflakeFriendlyId snowflakeFriendlyId;

    @Setup
    public void setup() {
        millisecondSnowflakeId = new MillisecondSnowflakeId(1);
        secondSnowflakeId = new SecondSnowflakeId(1);
        safeJsSecondSnowflakeId = SafeJavaScriptSnowflakeId.ofSecond(1);
        safeJsMillisecondSnowflakeId = SafeJavaScriptSnowflakeId.ofMillisecond(1);
        snowflakeFriendlyId = new DefaultSnowflakeFriendlyId(new ClockSyncSnowflakeId(new MillisecondSnowflakeId(1), ClockBackwardsSynchronizer.DEFAULT));
    }

    @Benchmark
    public long millisecondSnowflakeId_generate() {
        return retryWhenClockClockBackwards(millisecondSnowflakeId);
    }

    @Benchmark
    public SnowflakeIdState millisecondSnowflakeId_friendlyId() {
        return snowflakeFriendlyId.friendlyId();
    }

    @Benchmark
    public long secondSnowflakeId_generate() {
        return retryWhenClockClockBackwards(secondSnowflakeId);
    }

    @Benchmark
    public long safeJsMillisecondSnowflakeId_generate() {
        return retryWhenClockClockBackwards(safeJsMillisecondSnowflakeId);
    }

    @Benchmark
    public long safeJsSecondSnowflakeId_generate() {
        return retryWhenClockClockBackwards(safeJsSecondSnowflakeId);
    }

    public long retryWhenClockClockBackwards(IdGenerator idGenerator) {
        try {
            return idGenerator.generate();
        } catch (ClockBackwardsException clockBackwardsException) {
            System.out.println(idGenerator.getClass().getSimpleName() + ":" + clockBackwardsException.getMessage());
            return retryWhenClockClockBackwards(idGenerator);
        }
    }
}
