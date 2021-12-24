/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
 * @author ahoo wang
 */
public class IdSegmentDistributorDefinition {
    private final String namespace;
    private final String name;
    private final long offset;
    private final long step;

    public IdSegmentDistributorDefinition(String namespace, String name, long offset, long step) {
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.step = step;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public String getNamespacedName() {
        return IdSegmentDistributor.getNamespacedName(getNamespace(), getName());
    }

    public long getOffset() {
        return offset;
    }

    public long getStep() {
        return step;
    }
}
