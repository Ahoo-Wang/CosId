package me.ahoo.cosid.jvm;

import me.ahoo.cosid.IdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 * Creation time: 2019/11/21 20:51
 */
public class JdkId implements IdGenerator {
    public final static IdGenerator INSTANCE = new JdkId();
    private final AtomicLong idGen = new AtomicLong();

    @Override
    public long generate() {
        return idGen.incrementAndGet();
    }
}
