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

import me.ahoo.cosid.annotation.accessor.CosIdAccessor;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.annotation.accessor.CosIdAccessorSupport;

import javax.annotation.concurrent.ThreadSafe;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class CosIdAnnotationSupport {

    private final IdGeneratorProvider idGeneratorProvider;

    public CosIdAnnotationSupport(IdGeneratorProvider idGeneratorProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
    }

    public boolean ensureId(Object entity) {
        CosIdAccessor cosIdAccessor = CosIdAccessorSupport.getCosIdAccessor(entity.getClass());
        if (CosIdAccessor.NOT_FOUND.equals(cosIdAccessor)) {
            return false;
        }
        return cosIdAccessor.ensureId(entity, idGeneratorProvider);
    }

}
