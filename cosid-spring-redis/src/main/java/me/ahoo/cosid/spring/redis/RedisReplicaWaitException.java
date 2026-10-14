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

package me.ahoo.cosid.spring.redis;

import me.ahoo.cosid.CosIdException;

import java.time.Duration;

/**
 * Thrown when fewer replicas than {@link RedisReplicaWait#getReplicas()} acknowledged a write within its timeout.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public class RedisReplicaWaitException extends CosIdException {
    private final String operation;
    private final int replicas;
    private final long acknowledged;
    
    public RedisReplicaWaitException(String operation, int replicas, long acknowledged, Duration timeout) {
        super("Redis operation [" + operation + "]: " + acknowledged + " of " + replicas + " replicas acknowledged the write within " + timeout
            + ". A failover now could lose it.");
        this.operation = operation;
        this.replicas = replicas;
        this.acknowledged = acknowledged;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public int getReplicas() {
        return replicas;
    }
    
    public long getAcknowledged() {
        return acknowledged;
    }
}
