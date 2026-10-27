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

import me.ahoo.cosid.runtime.CosIdRuntime;

/**
 * Activiti's {@code IdGenerator} on the text ids of a runtime generator: set it with
 * {@code processEngineConfiguration.setIdGenerator(new ActivitiIdGenerator(runtime))}. The generator is the one named
 * here (in Spring {@code cosid.activiti.generator-name}), else by the {@link #ID_KEY} system property, else
 * {@code default}.
 */
public class ActivitiIdGenerator implements org.activiti.engine.impl.cfg.IdGenerator {
    /** The system property of the generator name. */
    public static final String ID_KEY = "cosid.activiti";
    /** The generator when neither a name nor the system property gives one. */
    public static final String DEFAULT_GENERATOR = "default";

    private final TextIds ids;

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
        this.ids = TextIds.of(runtime, generator != null && !generator.isBlank() ? generator : System.getProperty(ID_KEY, DEFAULT_GENERATOR));
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    public String generator() {
        return ids.generator();
    }

    @Override
    public String getNextId() {
        return ids.get();
    }
}
