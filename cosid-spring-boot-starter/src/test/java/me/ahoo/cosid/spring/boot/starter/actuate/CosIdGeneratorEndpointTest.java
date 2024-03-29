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

package me.ahoo.cosid.spring.boot.starter.actuate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

class CosIdGeneratorEndpointTest {
    private final IdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
    private final CosIdGeneratorEndpoint cosIdGeneratorEndpoint = new CosIdGeneratorEndpoint(idGeneratorProvider);
    
    public CosIdGeneratorEndpointTest() {
        idGeneratorProvider.setShare(MockIdGenerator.INSTANCE);
    }
    
    @Test
    void shareGenerate() {
        var id = cosIdGeneratorEndpoint.shareGenerate();
        assertThat(id, greaterThan(0L));
    }
    
    @Test
    void generate() {
        var id = cosIdGeneratorEndpoint.generate(IdGeneratorProvider.SHARE);
        assertThat(id, greaterThan(0L));
    }
}