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


package me.ahoo.cosid.spring.data.jdbc;

import me.ahoo.cosid.IntIdGenerator;
import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;

import java.util.Objects;

/**
 * Fills the ids of a Spring Data JDBC aggregate before it is converted for an insert: its
 * {@link me.ahoo.cosid.accessor.CosId} fields through the runtime's {@link IdFiller}, then its {@code @Id} property, when
 * still unset, from the {@code idGenerator} (as 4.x did with the shared generator). The {@code @Id} goes through Spring
 * Data's property accessor, so an immutable aggregate (a record, a wither) comes back as a new instance.
 *
 * <p>An unset id is {@code null}, a number {@code <= 0} or an empty string; {@code long}, {@code int} and {@code String}
 * ids are supported, other id types are left to the application.
 */
public class CosIdBeforeConvertCallback implements BeforeConvertCallback<Object> {
    /** The generator of {@code @Id} properties by default. */
    public static final String DEFAULT_ID_GENERATOR = "default";

    private final CosIdRuntime runtime;
    private final RelationalMappingContext mappingContext;
    private final String idGenerator;

    /**
     * A callback that fills {@code @Id} properties from the {@code default} generator.
     *
     * @param runtime        the runtime
     * @param mappingContext the mapping context of the aggregates
     */
    public CosIdBeforeConvertCallback(CosIdRuntime runtime, RelationalMappingContext mappingContext) {
        this(runtime, mappingContext, DEFAULT_ID_GENERATOR);
    }

    /**
     * A callback.
     *
     * @param runtime        the runtime
     * @param mappingContext the mapping context of the aggregates
     * @param idGenerator    the generator of {@code @Id} properties
     */
    public CosIdBeforeConvertCallback(CosIdRuntime runtime, RelationalMappingContext mappingContext, String idGenerator) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mappingContext = Objects.requireNonNull(mappingContext, "mappingContext");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public Object onBeforeConvert(Object aggregate) {
        runtime.filler().fill(aggregate);
        PersistentEntity<?, RelationalPersistentProperty> entity = mappingContext.getRequiredPersistentEntity(aggregate.getClass());
        RelationalPersistentProperty id = entity.getIdProperty();
        if (id == null) {
            return aggregate;
        }
        PersistentPropertyAccessor<Object> accessor = entity.getPropertyAccessor(aggregate);
        if (!isUnset(accessor.getProperty(id))) {
            return aggregate;
        }
        Class<?> type = id.getType();
        Object value;
        if (type == long.class || type == Long.class) {
            value = runtime.generator(idGenerator).generate();
        } else if (type == int.class || type == Integer.class) {
            value = runtime.get(idGenerator, IntIdGenerator.class).generate();
        } else if (type == String.class) {
            value = runtime.text(idGenerator).get();
        } else {
            return aggregate;
        }
        accessor.setProperty(id, value);
        return accessor.getBean();
    }

    private static boolean isUnset(Object value) {
        return switch (value) {
            case null -> true;
            case Number number -> number.longValue() <= 0;
            case String text -> text.isEmpty();
            default -> false;
        };
    }
}
