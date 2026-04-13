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

package me.ahoo.cosid.segment;

/**
 * Definition for an ID segment distributor configuration.
 *
 * <p>Holds the configuration parameters needed to allocate ID segments
 * from a distributor (namespace, name, offset, step).
 *
 * @author ahoo wang
 */
public class IdSegmentDistributorDefinition {
    private final String namespace;
    private final String name;
    private final long offset;
    private final long step;

    /**
     * Creates a new definition.
     *
     * @param namespace the namespace
     * @param name      the segment name
     * @param offset    the starting offset
     * @param step      the step size for segment allocation
     */
    public IdSegmentDistributorDefinition(String namespace, String name, long offset, long step) {
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.step = step;
    }

    /**
     * Gets the namespace.
     *
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the segment name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the full namespaced name.
     *
     * @return namespace.name
     */
    public String getNamespacedName() {
        return IdSegmentDistributor.getNamespacedName(getNamespace(), getName());
    }

    /**
     * Gets the starting offset.
     *
     * @return the offset
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Gets the step size.
     *
     * @return the step
     */
    public long getStep() {
        return step;
    }
}
