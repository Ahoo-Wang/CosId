/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;

/**
 * @author ahoo wang
 */
@Slf4j
public class ZookeeperIdSegmentDistributor implements IdSegmentDistributor {

    private final String namespace;
    private final String name;
    private final long offset;
    private final long step;
    private final String counterPath;
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
        this.counterPath = Strings.lenientFormat("/%s/%s/%s", CosId.COSID, namespace, name);
        this.distributedAtomicLong = new DistributedAtomicLong(curatorFramework, counterPath, retryPolicy);
        this.ensureOffset();
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getStep() {
        return step;
    }

    private void ensureOffset() {
        if (log.isDebugEnabled()) {
            log.debug("ensureOffset -[{}]- offset:[{}].", counterPath, offset);
        }
        boolean notExists;
        try {
            notExists = distributedAtomicLong.initialize(offset);
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }

        if (log.isDebugEnabled()) {
            log.debug("ensureOffset -[{}]- offset:[{}] - notExists:[{}].", counterPath, offset, notExists);
        }
    }

    @Override
    public long nextMaxId(long step) {
        IdSegmentDistributor.ensureStep(step);

        try {
            AtomicValue<Long> nextMaxId = distributedAtomicLong.add(step);
            return nextMaxId.postValue();
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception.getCause());
        }
    }
}
