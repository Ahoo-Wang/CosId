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

package me.ahoo.cosid.proxy;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.test.snowflake.machine.distributor.MachineIdDistributorSpec;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * ProxyMachineIdDistributorTest .
 *
 * @author ahoo wang
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class ProxyMachineIdDistributorTest extends MachineIdDistributorSpec {
    public final static String PROXY_HOST = "http://localhost:8088";
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new ProxyMachineIdDistributor(new OkHttpClient(), PROXY_HOST, MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    public void guardLost() {
        //TODO
        //super.guardLost();
    }
}
