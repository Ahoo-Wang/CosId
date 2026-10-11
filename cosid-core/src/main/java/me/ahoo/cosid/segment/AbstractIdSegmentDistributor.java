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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Base of the {@link IdSegmentDistributor}s: common properties and one rollback rule for every backend.
 *
 * <p>The max id this distributor got from the store must strictly move forward. If the store hands out a max id below
 * {@code last max id + step} (restored from a backup, a lost Redis key, a manual edit), the range overlaps ids already
 * issued, and {@link #nextMaxId(long)} throws {@link IllegalStateException} instead of issuing duplicates. Subclasses
 * only implement {@link #doNextMaxId(long)}.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public abstract class AbstractIdSegmentDistributor implements IdSegmentDistributor {
    private final String namespace;
    private final String name;
    private final long step;
    private final AtomicLong lastMaxId = new AtomicLong();
    
    protected AbstractIdSegmentDistributor(String namespace, String name, long step) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name can not be empty!");
        IdSegmentDistributor.ensureStep(step);
        this.namespace = namespace;
        this.name = name;
        this.step = step;
    }
    
    @Override
    public @NonNull String getNamespace() {
        return namespace;
    }
    
    @Override
    public @NonNull String getName() {
        return name;
    }
    
    @Override
    public long getStep() {
        return step;
    }
    
    /**
     * The largest max id this distributor got so far, {@code 0} before the first one.
     */
    public long getLastMaxId() {
        return lastMaxId.get();
    }
    
    @Override
    public long nextMaxId(long step) {
        IdSegmentDistributor.ensureStep(step);
        final long nextMinMaxId = lastMaxId.get() + step;
        long nextMaxId = doNextMaxId(step);
        Preconditions.checkState(nextMaxId >= nextMinMaxId, "nextMaxId:[%s] must be greater than nextMinMaxId:[%s]!", nextMaxId, nextMinMaxId);
        lastMaxId.accumulateAndGet(nextMaxId, Math::max);
        return nextMaxId;
    }
    
    /**
     * Ask the store for the next max id, {@code step} after the previous one.
     *
     * @param step the step, already validated
     * @return the new max id
     */
    protected abstract long doNextMaxId(long step);
}
