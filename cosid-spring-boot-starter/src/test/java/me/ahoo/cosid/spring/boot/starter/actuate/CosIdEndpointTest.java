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
import me.ahoo.cosid.stat.generator.IdGeneratorStat;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

import java.util.Map;

class CosIdEndpointTest {
    private final IdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
    private final CosIdEndpoint cosIdEndpoint = new CosIdEndpoint(idGeneratorProvider);
    
    public CosIdEndpointTest() {
        idGeneratorProvider.setShare(MockIdGenerator.INSTANCE);
    }
    
    @Test
    void stat() {
        Map<String, IdGeneratorStat> stat = cosIdEndpoint.stat();
        assertThat(stat, notNullValue());
        assertThat(stat.size(), equalTo(1));
    }
    
    @Test
    void getStat() {
        IdGeneratorStat stat = cosIdEndpoint.getStat(IdGeneratorProvider.SHARE);
        assertThat(stat, notNullValue());
    }
    
    @Test
    void remove() {
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        CosIdEndpoint cosIdEndpoint = new CosIdEndpoint(idGeneratorProvider);
        idGeneratorProvider.setShare(MockIdGenerator.INSTANCE);
        cosIdEndpoint.remove(IdGeneratorProvider.SHARE);
        assertThat(idGeneratorProvider.getShare(), nullValue());
    }
}