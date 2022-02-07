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

package me.ahoo.cosid.accessor.scanner;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.accessor.CosIdAccessor;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;

/**
 * Default {@link CosIdScanner} implementation.
 *
 * @author ahoo wang
 */
@Slf4j
public class DefaultCosIdScanner implements CosIdScanner {

    private final String[] basePackages;
    private final CosIdAccessorParser cosIdAccessorParser;
    private final CosIdAccessorRegistry cosIdAccessorRegistry;

    public DefaultCosIdScanner(String[] basePackages, FieldDefinitionParser fieldDefinitionParser, CosIdAccessorRegistry cosIdAccessorRegistry) {
        this(basePackages, new DefaultAccessorParser(fieldDefinitionParser), cosIdAccessorRegistry);
    }

    public DefaultCosIdScanner(String[] basePackages, CosIdAccessorParser cosIdAccessorParser, CosIdAccessorRegistry cosIdAccessorRegistry) {
        this.basePackages = basePackages;
        this.cosIdAccessorRegistry = cosIdAccessorRegistry;
        this.cosIdAccessorParser = cosIdAccessorParser;
    }

    @Override
    public void scan() {
        if (log.isInfoEnabled()) {
            log.info("scan - basePackages:{}.", Arrays.toString(basePackages));
        }
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            ClassPath classPath = ClassPath.from(classLoader);
            for (String basePackage : basePackages) {

                ImmutableSet<ClassPath.ClassInfo> classInfos = classPath.getTopLevelClassesRecursive(basePackage);
                for (ClassPath.ClassInfo classInfo : classInfos) {
                    Class<?> clazz = classLoader.loadClass(classInfo.getName());
                    if (clazz.isInterface()) {
                        continue;
                    }
                    CosIdAccessor cosIdAccessor = cosIdAccessorParser.parse(clazz);
                    cosIdAccessorRegistry.register(clazz, cosIdAccessor);
                }
            }
        } catch (IOException e) {
            throw new CosIdException(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }
}
