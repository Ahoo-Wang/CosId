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

package me.ahoo.cosid.accessor;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.LazyIdGenerator;


/**
 * Abstract {@link IdMetadata}.
 *
 * @author ahoo wang
 */
public abstract class AbstractIdMetadata implements IdMetadata {

    private final IdDefinition idDefinition;
    private final LazyIdGenerator idGenerator;

    public AbstractIdMetadata(IdDefinition idDefinition) {
        this.idDefinition = idDefinition;
        this.idGenerator = new LazyIdGenerator(idDefinition.getGeneratorName());
    }

    @Override
    public IdDefinition getIdDefinition() {
        return idDefinition;
    }

    @Override
    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

}
