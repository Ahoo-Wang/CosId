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

import me.ahoo.cosid.accessor.IdFiller;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.util.Map;
import java.util.Objects;

/**
 * Fills the {@link me.ahoo.cosid.accessor.CosId} ids of an INSERT's parameter before it runs: the entity itself, or each
 * entity of the collection under {@code listKey} of a multi-parameter map (a batch insert,
 * {@code insertAll(@Param("list") List<Order> orders)}).
 */
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class CosIdInterceptor implements Interceptor {
    /** The parameter name of a batch insert's entities by default, MyBatis' own for a single collection parameter. */
    public static final String DEFAULT_LIST_KEY = "list";

    private final IdFiller filler;
    private final String listKey;

    /**
     * An interceptor of the default list key.
     *
     * @param filler the filler
     */
    public CosIdInterceptor(IdFiller filler) {
        this(filler, DEFAULT_LIST_KEY);
    }

    /**
     * An interceptor.
     *
     * @param filler  the filler
     * @param listKey the parameter name of a batch insert's entities
     */
    public CosIdInterceptor(IdFiller filler, String listKey) {
        this.filler = Objects.requireNonNull(filler, "filler");
        this.listKey = Objects.requireNonNull(listKey, "listKey");
    }

    /**
     * The parameter name of a batch insert's entities.
     *
     * @return the key
     */
    public String listKey() {
        return listKey;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        if (statement.getSqlCommandType() == SqlCommandType.INSERT) {
            fill(args[1]);
        }
        return invocation.proceed();
    }

    private void fill(Object parameter) {
        if (parameter instanceof Map<?, ?> parameters) {
            if (parameters.containsKey(listKey) && parameters.get(listKey) instanceof Iterable<?> entities) {
                for (Object entity : entities) {
                    if (entity != null) {
                        filler.fill(entity);
                    }
                }
            }
        } else if (parameter != null) {
            filler.fill(parameter);
        }
    }
}
