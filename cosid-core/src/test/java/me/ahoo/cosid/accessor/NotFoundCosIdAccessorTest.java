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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class NotFoundCosIdAccessorTest {
    @Test
    public void getIdDefinition() {
        assertThat(CosIdAccessor.NOT_FOUND.getIdDefinition(), nullValue());
    }
    
    @Test
    public void getIdGenerator() {
        assertThat(CosIdAccessor.NOT_FOUND.getIdGenerator(), nullValue());
    }
    
    @Test
    public void getIdField() {
        assertThat(CosIdAccessor.NOT_FOUND.getIdField(), nullValue());
    }
    
    @Test
    public void getId() {
        assertThat(CosIdAccessor.NOT_FOUND.getId(new Object()), nullValue());
    }
    
    @Test
    public void setId() {
        CosIdAccessor.NOT_FOUND.setId(new Object(), new Object());
    }
    
    @Test
    public void ensureId() {
        assertThat(CosIdAccessor.NOT_FOUND.ensureId(new Object()), equalTo(false));
    }
}