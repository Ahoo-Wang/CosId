package me.ahoo.cosid.redis.state;

import me.ahoo.cosid.jvm.JdkId;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class JdkIdState {

   public JdkId jdkId;

    @Setup
    public void setup() {
        jdkId = new JdkId();
    }
}
