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

package me.ahoo.cosid.cosid;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.jspecify.annotations.NonNull;

/**
 * CosIdGenerator that refuses to generate ids once the lease of its machine id is lost or expired.
 *
 * <p>Fail fast: a duplicate id is worse than a failed request. See {@link MachineIdLease}.
 */
public class LeasedCosIdGenerator implements CosIdGenerator, Decorator<CosIdGenerator> {
    private final CosIdGenerator actual;
    private final MachineIdLease lease;
    
    public LeasedCosIdGenerator(CosIdGenerator actual, MachineIdLease lease) {
        this.actual = actual;
        this.lease = lease;
    }
    
    @Override
    public @NonNull CosIdGenerator getActual() {
        return actual;
    }
    
    public MachineIdLease getLease() {
        return lease;
    }
    
    @Override
    public int getMachineId() {
        return actual.getMachineId();
    }
    
    @Override
    public long getLastTimestamp() {
        return actual.getLastTimestamp();
    }
    
    @Override
    public @NonNull CosIdIdStateParser getStateParser() {
        return actual.getStateParser();
    }
    
    @Override
    public @NonNull CosIdState generateAsState() throws MachineIdLostException {
        lease.ensureValid();
        return actual.generateAsState();
    }
    
    @Override
    public IdGeneratorStat stat() {
        return IdGeneratorStat.simple(getClass().getSimpleName(), actual.stat(), Stat.simple(getStateParser().getClass().getSimpleName()));
    }
}
