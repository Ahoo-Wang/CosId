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

package me.ahoo.cosid.zookeeper;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.util.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * Zookeeper IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class ZookeeperIdSegmentDistributor implements IdSegmentDistributor {
    
    private final String namespace;
    private final String name;
    private final long offset;
    private final long step;
    /**
     * /cosid/{namespace}.{name}
     */
    private final String counterPath;
    /**
     * {counterPath}-locker.
     */
    private final String counterLockerPath;
    private final DistributedAtomicLong distributedAtomicLong;
    
    public ZookeeperIdSegmentDistributor(String namespace, String name, long offset, long step, CuratorFramework curatorFramework, RetryPolicy retryPolicy) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name can not be empty!");
        Preconditions.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        Preconditions.checkArgument(step > 0, "step:[%s] must be greater than 0!", step);
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.step = step;
        this.counterPath = Strings.lenientFormat("/%s/%s", CosId.COSID, getNamespacedName());
        this.counterLockerPath = counterPath + "-locker";
        PromotedToLock promotedToLock = PromotedToLock.builder()
            .lockPath(counterLockerPath)
            .timeout(15, TimeUnit.SECONDS)
            .retryPolicy(retryPolicy)
            .build();
        this.distributedAtomicLong = new DistributedAtomicLong(curatorFramework, counterPath, retryPolicy, promotedToLock);
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
    
    void ensureOffset() {
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}].", counterPath, offset);
        }
        boolean notExists;
        try {
            notExists = distributedAtomicLong.initialize(offset);
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}] - notExists:[{}].", counterPath, offset, notExists);
        }
    }
    
    @Override
    public long nextMaxId(long step) {
        IdSegmentDistributor.ensureStep(step);
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}].", counterPath, step);
        }
        
        AtomicValue<Long> nextMaxId = Exceptions.invokeUnchecked(() -> distributedAtomicLong.add(step));
        
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}] - nextMaxId:[{} -> {}].", counterPath, step, nextMaxId.preValue(), nextMaxId.postValue());
        }
        if (!nextMaxId.succeeded()) {
            throw new CosIdException(Strings.lenientFormat("nextMaxId - [%s][%s->%s] concurrency conflict!", counterPath, nextMaxId.preValue(), nextMaxId.postValue()));
        }
        
        return nextMaxId.postValue();
    }
}
