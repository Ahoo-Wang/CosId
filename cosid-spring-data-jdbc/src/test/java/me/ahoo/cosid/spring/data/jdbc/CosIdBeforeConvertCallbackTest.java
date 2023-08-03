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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.accessor.parser.CompositeFieldDefinitionParser;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;

import java.util.Arrays;

class CosIdBeforeConvertCallbackTest {
    private final CosIdBeforeConvertCallback cosIdBeforeConvertCallback;
    
    public CosIdBeforeConvertCallbackTest() {
        var fieldDefinitionParser = new CompositeFieldDefinitionParser(Arrays.asList(AnnotationDefinitionParser.INSTANCE, IdAnnotationDefinitionParser.INSTANCE));
        DefaultAccessorParser accessorParser = new DefaultAccessorParser(fieldDefinitionParser);
        CosIdAccessorRegistry accessorRegistry = new DefaultAccessorRegistry(accessorParser);
        cosIdBeforeConvertCallback = new CosIdBeforeConvertCallback(accessorRegistry);
    }
    
    @Test
    void onBeforeConvertIfId() {
        var entity = new IdEntity();
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);
        cosIdBeforeConvertCallback.onBeforeConvert(entity);
        assertThat(entity.getId(), not(0));
    }
    
    @Test
    void onBeforeConvertIfCosId() {
        var entity = new CosIdEntity();
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);
        cosIdBeforeConvertCallback.onBeforeConvert(entity);
        assertThat(entity.getId(), not(0));
    }
    
    @Test
    void onBeforeConvertIfNotFound() {
        var entity = new NotFoundEntity();
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);
        cosIdBeforeConvertCallback.onBeforeConvert(entity);
        assertThat(entity.getId(), equalTo(0L));
    }
    
    static class IdEntity {
        @Id
        private long id;
        
        public long getId() {
            return id;
        }
        
        public IdEntity setId(int id) {
            this.id = id;
            return this;
        }
    }
    
    static class CosIdEntity {
        @CosId
        private long id;
        
        public long getId() {
            return id;
        }
        
        public CosIdEntity setId(int id) {
            this.id = id;
            return this;
        }
    }
    
    static class NotFoundEntity {
        
        private long id;
        
        public long getId() {
            return id;
        }
        
        public NotFoundEntity setId(int id) {
            this.id = id;
            return this;
        }
    }
}