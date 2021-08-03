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

package me.ahoo.cosid.support;

import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
public class CosIdAnnotationSupport {

    private final IdGeneratorProvider idGeneratorProvider;
    private final ConcurrentHashMap<Class<?>, List<CosIdField>> classMapField;

    public CosIdAnnotationSupport(IdGeneratorProvider idGeneratorProvider) {
        this.classMapField = new ConcurrentHashMap<>();
        this.idGeneratorProvider = idGeneratorProvider;
    }

    public void ensureId(Object entity) {
        getCosIdFields(entity.getClass()).forEach(cosIdField -> cosIdField.ensureId(entity, idGeneratorProvider));
    }

    private List<CosIdField> getCosIdFields(Class<?> entityClass) {
        return classMapField.computeIfAbsent(entityClass, (key) -> Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(CosId.class))
                .map(field -> {
                    CosId cosId = field.getAnnotation(CosId.class);
                    return new CosIdField(cosId, field);
                })
                .collect(Collectors.toList()));
    }
}
