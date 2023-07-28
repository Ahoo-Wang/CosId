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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdZookeeperMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.snowflake.ConditionalOnCosIdSnowflakeEnabled;
import me.ahoo.cosid.spring.boot.starter.snowflake.CosIdSnowflakeAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties;
import me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperProperties;
import me.ahoo.cosid.zookeeper.ZookeeperMachineIdDistributor;

import lombok.SneakyThrows;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;

/**
 * CosIdZookeeperMachineIdDistributorAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdZookeeperMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @SneakyThrows
    @Test
    void contextLoads() {
        try (TestingServer testingServer = new TestingServer()) {
            testingServer.start();
            this.contextRunner
                .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
                .withPropertyValues(MachineProperties.Distributor.TYPE + "=zookeeper")
                .withPropertyValues(CosIdZookeeperProperties.PREFIX + ".connect-string=" + testingServer.getConnectString())
                .withUserConfiguration(UtilAutoConfiguration.class,
                    CosIdAutoConfiguration.class,
                    CosIdZookeeperAutoConfiguration.class,
                    CosIdHostNameAutoConfiguration.class,
                    CosIdMachineAutoConfiguration.class)
                .withUserConfiguration(CosIdZookeeperMachineIdDistributorAutoConfiguration.class)
                .run(context -> {
                    assertThat(context)
                        .hasSingleBean(CosIdZookeeperMachineIdDistributorAutoConfiguration.class)
                        .hasSingleBean(ZookeeperMachineIdDistributor.class)
                    ;
                });
        }
    }
}
