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


package me.ahoo.cosid.activiti;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.tck.TestRuntimes;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** An in-memory Activiti engine whose ids come from a runtime generator. */
class ActivitiIdGeneratorTest {
    static CosIdRuntime runtime;

    @BeforeAll
    static void start() {
        runtime = TestRuntimes.start("activiti");
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(ActivitiIdGenerator.ID_KEY);
    }

    @Test
    void engineIdsComeFromTheGenerator() {
        ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
            .createStandaloneInMemProcessEngineConfiguration().setJdbcUrl("jdbc:h2:mem:activiti;DB_CLOSE_DELAY=-1");
        configuration.setIdGenerator(new ActivitiIdGenerator(runtime, TestRuntimes.ORDER));
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
        assertThat(new ActivitiIdGenerator(runtime).generator(), is(ActivitiIdGenerator.DEFAULT_GENERATOR));
        assertThat(new ActivitiIdGenerator(runtime, " ").generator(), is(ActivitiIdGenerator.DEFAULT_GENERATOR));
        System.setProperty(ActivitiIdGenerator.ID_KEY, TestRuntimes.BILL);
        ActivitiIdGenerator ids = new ActivitiIdGenerator(runtime);
        assertThat(ids.generator(), is(TestRuntimes.BILL));
        assertThat(ids.getNextId().length(), is(14));
        assertThat(new ActivitiIdGenerator(runtime, TestRuntimes.TOKEN).generator(), is(TestRuntimes.TOKEN));
    }
}
