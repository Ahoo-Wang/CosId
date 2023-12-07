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

import me.ahoo.cosid.mongo.MongoMachineCollection;
import me.ahoo.cosid.mongo.MongoMachineIdDistributor;
import me.ahoo.cosid.mongo.MongoMachineInitializer;
import me.ahoo.cosid.mongo.reactive.MongoReactiveMachineCollection;
import me.ahoo.cosid.mongo.reactive.MongoReactiveMachineInitializer;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.container.MongoLauncher;

import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;

class CosIdMongoMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues("cosid.namespace=" + MockIdGenerator.INSTANCE.generateAsString())
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .withPropertyValues("spring.data.mongodb.uri=" + MongoLauncher.getConnectionString())
            .withUserConfiguration(UtilAutoConfiguration.class,
                MongoAutoConfiguration.class,
                MongoReactiveAutoConfiguration.class,
                CosIdAutoConfiguration.class,
                CosIdHostNameAutoConfiguration.class,
                CosIdMachineAutoConfiguration.class,
                CosIdMongoMachineIdDistributorAutoConfiguration.class)
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .hasSingleBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                    .hasSingleBean(MachineProperties.class)
                    .hasSingleBean(MongoMachineInitializer.class)
                    .hasSingleBean(MongoMachineCollection.class)
                    .hasSingleBean(MongoMachineIdDistributor.class)
                    .hasSingleBean(MongoReactiveMachineInitializer.class)
                    .hasSingleBean(MongoReactiveMachineCollection.class)
                    .hasSingleBean(MongoMachineIdDistributor.class)
                ;
            });
    }
}