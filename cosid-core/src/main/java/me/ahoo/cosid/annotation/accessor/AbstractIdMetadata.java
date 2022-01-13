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

package me.ahoo.cosid.annotation.accessor;

import me.ahoo.cosid.annotation.CosIdDefinition;
import me.ahoo.cosid.annotation.IdMetadata;

import java.lang.reflect.Field;

/**
 *
 * @author ahoo wang
 */
public abstract class AbstractIdMetadata implements IdMetadata {

    private final CosIdDefinition cosIdDefinition;
    private final Field idField;

    public AbstractIdMetadata(CosIdDefinition cosIdDefinition, Field idField) {
        this.cosIdDefinition = cosIdDefinition;
        this.idField = idField;
    }

    @Override
    public CosIdDefinition getCosIdDefinition() {
        return cosIdDefinition;
    }

    @Override
    public Field getIdField() {
        return idField;
    }
}
