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

package me.ahoo.cosid.segment;

import java.util.function.Function;
import javax.annotation.concurrent.GuardedBy;

/**
 * Chained ID segment.
 *
 * @author ahoo wang
 */
public class IdSegmentChain implements IdSegment {
    public static final int ROOT_VERSION = -1;
    public static final IdSegmentChain NOT_SET = null;
    
    private final long version;
    private final IdSegment idSegment;
    @GuardedBy("this")
    private volatile IdSegmentChain next;
    private final boolean allowReset;
    
    public IdSegmentChain(IdSegmentChain previousChain, IdSegment idSegment, boolean allowReset) {
        this(previousChain.getVersion() + 1, idSegment, allowReset);
    }
    
    public IdSegmentChain(long version, IdSegment idSegment, boolean allowReset) {
        this.version = version;
        this.idSegment = idSegment;
        this.allowReset = allowReset;
    }
    
    /**
     * try set next Chained ID segment.
     *
     * @param idSegmentChainSupplier {@link IdSegmentChain} supplier
     * @return true if set successfully
     * @throws NextIdSegmentExpiredException This exception is thrown
     *                                       if the provided {@link IdSegmentChain} has expired.
     */
    public boolean trySetNext(Function<IdSegmentChain, IdSegmentChain> idSegmentChainSupplier) throws NextIdSegmentExpiredException {
        if (NOT_SET != next) {
            return false;
        }
        
        synchronized (this) {
            if (NOT_SET != next) {
                return false;
            }
            IdSegmentChain nextIdSegmentChain = idSegmentChainSupplier.apply(this);
            setNext(nextIdSegmentChain);
            return true;
        }
    }
    
    public void setNext(IdSegmentChain nextIdSegmentChain) {
        if (!allowReset) {
            ensureNextIdSegment(nextIdSegmentChain);
        }
        
        next = nextIdSegmentChain;
    }
    
    public IdSegmentChain ensureSetNext(Function<IdSegmentChain, IdSegmentChain> idSegmentChainSupplier) throws NextIdSegmentExpiredException {
        IdSegmentChain currentChain = this;
        while (!currentChain.trySetNext(idSegmentChainSupplier)) {
            currentChain = currentChain.getNext();
        }
        return currentChain;
    }
    
    public IdSegmentChain getNext() {
        return next;
    }
    
    public IdSegment getIdSegment() {
        return idSegment;
    }
    
    public long getVersion() {
        return version;
    }
    
    public int gap(IdSegmentChain end, long step) {
        return (int) ((end.getMaxId() - getSequence()) / step);
    }
    
    public static IdSegmentChain newRoot(boolean allowReset) {
        return new IdSegmentChain(IdSegmentChain.ROOT_VERSION, DefaultIdSegment.OVERFLOW, allowReset);
    }
    
    @Override
    public long getFetchTime() {
        return idSegment.getFetchTime();
    }
    
    @Override
    public long getTtl() {
        return idSegment.getTtl();
    }
    
    @Override
    public long getMaxId() {
        return idSegment.getMaxId();
    }
    
    @Override
    public long getOffset() {
        return idSegment.getOffset();
    }
    
    @Override
    public long getSequence() {
        return idSegment.getSequence();
    }
    
    @Override
    public long getStep() {
        return idSegment.getStep();
    }
    
    @Override
    public long incrementAndGet() {
        return idSegment.incrementAndGet();
    }
    
    @Override
    public String toString() {
        return "IdSegmentChain{"
            + "version=" + version
            + ", idSegment=" + idSegment
            + '}';
    }
}
