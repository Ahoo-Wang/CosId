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

import me.ahoo.cosid.segment.grouped.GroupedKey;

import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.function.Function;

/**
 * Chained ID segment for lock-free segment chain ID generation.
 *
 * <p>This class chains multiple ID segments together, allowing for seamless
 * transition between segments without blocking. When one segment is exhausted,
 * the next segment in the chain is used.
 *
 * @author ahoo wang
 */
public class IdSegmentChain implements IdSegment {
    /**
     * Version number for the root chain.
     */
    public static final int ROOT_VERSION = -1;
    /**
     * Sentinel value indicating next has not been set.
     */
    public static final IdSegmentChain NOT_SET = null;

    private final long version;
    private final IdSegment idSegment;
    @GuardedBy("this")
    private volatile IdSegmentChain next;
    private final boolean allowReset;

    /**
     * Creates a new chain segment linked to a previous chain.
     *
     * @param previousChain the previous chain in the link
     * @param idSegment    the ID segment for this chain
     * @param allowReset   whether reset is allowed
     */
    public IdSegmentChain(IdSegmentChain previousChain, IdSegment idSegment, boolean allowReset) {
        this(previousChain.getVersion() + 1, idSegment, allowReset);
    }

    /**
     * Creates a new chain segment with explicit version.
     *
     * @param version      the version number
     * @param idSegment    the ID segment for this chain
     * @param allowReset   whether reset is allowed
     */
    public IdSegmentChain(long version, IdSegment idSegment, boolean allowReset) {
        this.version = version;
        this.idSegment = idSegment;
        this.allowReset = allowReset;
    }

    /**
     * Attempts to set the next segment in the chain.
     *
     * <p>If next is already set, returns false without modifying.
     *
     * @param idSegmentChainSupplier supplier that creates the next segment based on this
     * @return true if set successfully, false if next was already set
     * @throws NextIdSegmentExpiredException if the provided segment has expired
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

    /**
     * Sets the next segment in the chain.
     *
     * @param nextIdSegmentChain the next segment
     */
    public void setNext(IdSegmentChain nextIdSegmentChain) {
        if (!allowReset) {
            ensureNextIdSegment(nextIdSegmentChain);
        }

        next = nextIdSegmentChain;
    }

    /**
     * Ensures the next segment is set, retrying until successful.
     *
     * @param idSegmentChainSupplier supplier that creates the next segment
     * @return the chain that has next set
     * @throws NextIdSegmentExpiredException if all segments expire
     */
    public IdSegmentChain ensureSetNext(Function<IdSegmentChain, IdSegmentChain> idSegmentChainSupplier) throws NextIdSegmentExpiredException {
        IdSegmentChain currentChain = this;
        while (!currentChain.trySetNext(idSegmentChainSupplier)) {
            currentChain = currentChain.getNext();
        }
        return currentChain;
    }

    /**
     * Gets the next segment in the chain.
     *
     * @return the next segment or null if not yet set
     */
    public IdSegmentChain getNext() {
        return next;
    }

    /**
     * Gets the ID segment for this chain.
     *
     * @return the ID segment
     */
    public IdSegment getIdSegment() {
        return idSegment;
    }

    @Override
    public GroupedKey group() {
        return idSegment.group();
    }

    /**
     * Gets the version number of this chain.
     *
     * @return the version
     */
    public long getVersion() {
        return version;
    }

    /**
     * Calculates the gap between this chain's sequence and another's max ID.
     *
     * @param end  the end chain
     * @param step the step size
     * @return the number of IDs between sequences
     */
    public int gap(IdSegmentChain end, long step) {
        return (int) ((end.getMaxId() - getSequence()) / step);
    }

    /**
     * Creates a new root chain.
     *
     * @param allowReset whether reset is allowed
     * @return a new root chain
     */
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
