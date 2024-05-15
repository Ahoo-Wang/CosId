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

package me.ahoo.cosid.mybatis;

import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * CosId Plugin.
 *
 * @author ahoo wang
 */
@Intercepts({@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})})
public class CosIdPlugin implements Interceptor {

    public static final String DEFAULT_LIST_KEY = "list";
    private final CosIdAccessorRegistry accessorRegistry;
    private final String listKey;

    public CosIdPlugin(CosIdAccessorRegistry accessorRegistry) {
        this(accessorRegistry, DEFAULT_LIST_KEY);
    }

    public CosIdPlugin(CosIdAccessorRegistry accessorRegistry, String listKey) {
        this.accessorRegistry = accessorRegistry;
        this.listKey = listKey;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        if (!SqlCommandType.INSERT.equals(statement.getSqlCommandType())) {
            return invocation.proceed();
        }

        Object parameter = args[1];
        if (Objects.isNull(parameter)) {
            return invocation.proceed();
        }

        if (!(parameter instanceof Map)) {
            accessorRegistry.ensureId(parameter);
            return invocation.proceed();
        }
        boolean hasList = ((Map) parameter).containsKey(listKey);
        if (!hasList) {
            return invocation.proceed();
        }
        Collection entityList = (Collection) ((Map) parameter).get(listKey);
        if (Objects.isNull(entityList)) {
            return invocation.proceed();
        }
        for (Object entity : entityList) {
            accessorRegistry.ensureId(entity);
        }
        return invocation.proceed();
    }
}
