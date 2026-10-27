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


package me.ahoo.cosid.integrations.workflow;

import me.ahoo.cosid.runtime.CosIdRuntime;

/**
 * Activiti's {@code IdGenerator} on a runtime generator (see {@link WorkflowIds}): set it with
 * {@code processEngineConfiguration.setIdGenerator(new ActivitiIdGenerator(runtime))}.
 */
public class ActivitiIdGenerator extends WorkflowIds implements org.activiti.engine.impl.cfg.IdGenerator {
    /** The system property of the generator name. */
    public static final String ID_KEY = "cosid.activiti";

    /**
     * The generator of the {@link #ID_KEY} system property, else {@code default}.
     *
     * @param runtime the runtime
     */
    public ActivitiIdGenerator(CosIdRuntime runtime) {
        this(runtime, null);
    }

    /**
     * A named generator.
     *
     * @param runtime   the runtime
     * @param generator the generator name; blank or {@code null} for the {@link #ID_KEY} system property's
     */
    public ActivitiIdGenerator(CosIdRuntime runtime, String generator) {
        super(runtime, generatorName(generator, ID_KEY));
    }
}
