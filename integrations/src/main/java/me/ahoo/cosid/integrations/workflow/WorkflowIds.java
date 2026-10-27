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

import me.ahoo.cosid.integrations.TextIds;
import me.ahoo.cosid.runtime.CosIdRuntime;

/**
 * The id generator of a workflow engine, Activiti ({@link ActivitiIdGenerator}) or Flowable
 * ({@link FlowableIdGenerator}): the text ids of a runtime generator. The generator is the one named in Spring
 * ({@code cosid.activiti.generator-name}, {@code cosid.flowable.generator-name}), else by the system property of the
 * engine ({@code cosid.activiti}, {@code cosid.flowable}), else {@code default}.
 */
public abstract class WorkflowIds {
    /** The generator when neither a name nor the system property gives one. */
    public static final String DEFAULT_GENERATOR = "default";

    private final TextIds ids;

    /**
     * The ids of a named generator.
     *
     * @param runtime   the runtime
     * @param generator the generator name
     */
    protected WorkflowIds(CosIdRuntime runtime, String generator) {
        this.ids = TextIds.of(runtime, generator);
    }

    /**
     * The generator name: the given one unless blank, else the system property's, else {@link #DEFAULT_GENERATOR}.
     *
     * @param generator   the given name, may be {@code null}
     * @param propertyKey the system property
     * @return the name
     */
    protected static String generatorName(String generator, String propertyKey) {
        if (generator != null && !generator.isBlank()) {
            return generator;
        }
        return System.getProperty(propertyKey, DEFAULT_GENERATOR);
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    public String generator() {
        return ids.generator();
    }

    /**
     * The next id.
     *
     * @return the id
     */
    public String getNextId() {
        return ids.get();
    }
}
