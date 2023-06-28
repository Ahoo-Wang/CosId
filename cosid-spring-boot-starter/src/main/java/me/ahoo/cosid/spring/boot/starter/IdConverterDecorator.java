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

package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;

import com.google.common.base.Strings;

import java.lang.reflect.InvocationTargetException;

public abstract class IdConverterDecorator<T extends IdGenerator> {
    protected final T idGenerator;
    protected final IdConverterDefinition converterDefinition;
    
    protected IdConverterDecorator(T idGenerator, IdConverterDefinition converterDefinition) {
        this.idGenerator = idGenerator;
        this.converterDefinition = converterDefinition;
    }
    
    public T decorate() {
        IdConverter idConverter = ToStringIdConverter.INSTANCE;
        switch (converterDefinition.getType()) {
            case TO_STRING -> idConverter = newToString(idConverter);
            case RADIX -> idConverter = newRadix();
            case SNOWFLAKE_FRIENDLY -> idConverter = newSnowflakeFriendly();
            case CUSTOM -> idConverter = newCustom();
            default -> throw new IllegalStateException("Unexpected value: " + converterDefinition.getType());
        }
        
        if (!Strings.isNullOrEmpty(converterDefinition.getPrefix())) {
            idConverter = new PrefixIdConverter(converterDefinition.getPrefix(), idConverter);
        }
        if (!Strings.isNullOrEmpty(converterDefinition.getSuffix())) {
            idConverter = new SuffixIdConverter(converterDefinition.getSuffix(), idConverter);
        }
        
        return newIdGenerator(idConverter);
    }
    
    protected IdConverter newRadix() {
        IdConverterDefinition.Radix radix = converterDefinition.getRadix();
        return Radix62IdConverter.of(radix.isPadStart(), radix.getCharSize());
    }
    
    protected IdConverter newToString(IdConverter defaultIdConverter) {
        IdConverterDefinition.ToString toString = converterDefinition.getToString();
        if (toString != null) {
            return new ToStringIdConverter(toString.isPadStart(), toString.getCharSize());
        }
        return defaultIdConverter;
    }
    
    protected IdConverter newSnowflakeFriendly() {
        throw new UnsupportedOperationException("newSnowflakeFriendly");
    }
    
    protected IdConverter newCustom() {
        IdConverterDefinition.Custom custom = converterDefinition.getCustom();
        try {
            return custom.getType().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected abstract T newIdGenerator(IdConverter idConverter);
}
