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


package me.ahoo.cosid.flowable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.tck.TestRuntimes;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** An in-memory Flowable engine whose ids come from a runtime generator. */
class FlowableIdGeneratorTest {
    static CosIdRuntime runtime;

    @BeforeAll
    static void start() {
        runtime = TestRuntimes.start("flowable");
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(FlowableIdGenerator.ID_KEY);
    }

    @Test
    void engineIdsComeFromTheGenerator() {
        ProcessEngineConfiguration configuration = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
            .setJdbcUrl("jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1");
        configuration.setIdGenerator(new FlowableIdGenerator(runtime, TestRuntimes.ORDER));
        ProcessEngine engine = configuration.buildProcessEngine();
        try {
            Deployment deployment = engine.getRepositoryService().createDeployment().addClasspathResource("one-task.bpmn20.xml").deploy();
            assertThat(deployment.getId(), startsWith("O"));
            ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("oneTask");
            assertThat(instance.getId(), startsWith("O"));
            assertThat(engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getId(), startsWith("O"));
        } finally {
            engine.close();
        }
    }

    @Test
    void generatorFromTheSystemPropertyElseDefault() {
        assertThat(new FlowableIdGenerator(runtime).generator(), is(FlowableIdGenerator.DEFAULT_GENERATOR));
        assertThat(new FlowableIdGenerator(runtime, " ").generator(), is(FlowableIdGenerator.DEFAULT_GENERATOR));
        System.setProperty(FlowableIdGenerator.ID_KEY, TestRuntimes.BILL);
        FlowableIdGenerator ids = new FlowableIdGenerator(runtime);
        assertThat(ids.generator(), is(TestRuntimes.BILL));
        assertThat(ids.getNextId().length(), is(14));
        assertThat(new FlowableIdGenerator(runtime, TestRuntimes.TOKEN).generator(), is(TestRuntimes.TOKEN));
    }
}
