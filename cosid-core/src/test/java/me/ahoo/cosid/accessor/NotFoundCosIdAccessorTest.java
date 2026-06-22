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
    void sentinelShouldExposeNoMetadata() {
        assertThat(CosIdAccessor.NOT_FOUND.getIdDefinition(), nullValue());
        assertThat(CosIdAccessor.NOT_FOUND.getIdGenerator(), nullValue());
        assertThat(CosIdAccessor.NOT_FOUND.getIdField(), nullValue());
    }

    @Test
    void getIdShouldAlwaysReturnNullForAnyTarget() {
        Object target = new Object();

        assertThat(CosIdAccessor.NOT_FOUND.getId(target), nullValue());
        assertThat(CosIdAccessor.NOT_FOUND.getId(null), nullValue());
    }

    @Test
    void setIdShouldBeNoOpForAnyTargetAndValue() {
        Object target = new Object();

        CosIdAccessor.NOT_FOUND.setId(target, 1L);
        CosIdAccessor.NOT_FOUND.setId(null, null);

        assertThat(CosIdAccessor.NOT_FOUND.getId(target), nullValue());
    }

    @Test
    void ensureIdShouldNeverGenerateId() {
        assertThat(CosIdAccessor.NOT_FOUND.ensureId(new Object()), equalTo(false));
        assertThat(CosIdAccessor.NOT_FOUND.ensureId(null), equalTo(false));
    }

    @Test
    void availableTypeShouldOnlyAcceptSupportedIdTypes() {
        assertThat(CosIdAccessor.availableType(String.class), equalTo(true));
        assertThat(CosIdAccessor.availableType(Long.class), equalTo(true));
        assertThat(CosIdAccessor.availableType(long.class), equalTo(true));
        assertThat(CosIdAccessor.availableType(Integer.class), equalTo(true));
        assertThat(CosIdAccessor.availableType(int.class), equalTo(true));
        assertThat(CosIdAccessor.availableType(Double.class), equalTo(false));
        assertThat(CosIdAccessor.availableType(Object.class), equalTo(false));
    }
}
