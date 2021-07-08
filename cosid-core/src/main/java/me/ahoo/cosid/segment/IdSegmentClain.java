/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosid.segment;

import java.util.function.Function;

/**
 * @author ahoo wang
 */
public class IdSegmentClain implements IdSegment {
    public static final int ROOT_VERSION = -1;
    public static final IdSegmentClain NOT_SET = null;

    private final int version;
    private final IdSegment idSegment;
    private volatile IdSegmentClain next;

    public IdSegmentClain(IdSegmentClain previousClain, IdSegment idSegment) {
        this(previousClain.getVersion() + 1, idSegment);
    }

    public IdSegmentClain(int version, IdSegment idSegment) {
        this.version = version;
        this.idSegment = idSegment;
    }

    public boolean trySetNext(Function<IdSegmentClain, IdSegmentClain> idSegmentClainSupplier) throws NextIdSegmentExpiredException {
        if (NOT_SET != next) {
            return false;
        }

        synchronized (this) {
            if (NOT_SET != next) {
                return false;
            }
            IdSegmentClain nextIdSegmentClain = idSegmentClainSupplier.apply(this);
            setNext(nextIdSegmentClain);
            return true;
        }
    }

    public void setNext(IdSegmentClain nextIdSegmentClain) {
        ensureNextIdSegment(nextIdSegmentClain);
        next = nextIdSegmentClain;
    }

    public IdSegmentClain ensureSetNext(Function<IdSegmentClain, IdSegmentClain> idSegmentClainSupplier) throws NextIdSegmentExpiredException {
        IdSegmentClain currentClain = this;
        while (!currentClain.trySetNext(idSegmentClainSupplier)) {
            currentClain = currentClain.getNext();
        }
        return currentClain;
    }

    public IdSegmentClain getNext() {
        return next;
    }

    public IdSegment getIdSegment() {
        return idSegment;
    }

    public int getVersion() {
        return version;
    }

    public int gap(IdSegmentClain end) {
        return end.version - version;
    }

    public static IdSegmentClain newRoot() {
        return new IdSegmentClain(IdSegmentClain.ROOT_VERSION, DefaultIdSegment.OVERFLOW);
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
    public int getStep() {
        return idSegment.getStep();
    }

    @Override
    public long incrementAndGet() {
        return idSegment.incrementAndGet();
    }

    @Override
    public String toString() {
        return "IdSegmentClain{" +
                "version=" + version +
                ", idSegment=" + idSegment +
                '}';
    }
}
