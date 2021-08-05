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

package me.ahoo.cosid.annotation;

import me.ahoo.cosid.provider.IdGeneratorProvider;

/**
 * @author ahoo wang
 */
public class CosIdDefinition {

    private final String generatorName;
    private final boolean friendlyId;

    public CosIdDefinition() {
        this(IdGeneratorProvider.SHARE, false);
    }

    public CosIdDefinition(String generatorName, boolean friendlyId) {
        this.generatorName = generatorName;
        this.friendlyId = friendlyId;
    }

    public String getGeneratorName() {
        return generatorName;
    }

    public boolean isFriendlyId() {
        return friendlyId;
    }

    public static CosIdDefinition of(CosId cosId) {
        return new CosIdDefinition(cosId.value(), cosId.friendlyId());
    }
}
