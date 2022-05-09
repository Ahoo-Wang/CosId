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

package me.ahoo.cosid.provider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;

import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

/**
 * LazyIdGeneratorTest .
 *
 * @author ahoo wang
 */
class LazyIdGeneratorTest {
    
    @Test
    void getGeneratorName() {
        String generatorName = "getGeneratorName";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        assertThat(lazyIdGenerator.getGeneratorName(), equalTo(generatorName));
    }
    
    @Test
    void tryGet() {
        String generatorName = "tryGet";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        assertThat(lazyIdGenerator.tryGet(false), nullValue());
        DefaultIdGeneratorProvider.INSTANCE.set(generatorName, MockIdGenerator.INSTANCE);
        assertThat(lazyIdGenerator.tryGet(false), equalTo(MockIdGenerator.INSTANCE));
    }
    
    @Test
    void asSnowflakeId() {
        String generatorName = "asSnowflakeId";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        DefaultIdGeneratorProvider.INSTANCE.set(generatorName, new MillisecondSnowflakeId(1));
        assertThat(lazyIdGenerator.asSnowflakeId(false), isA(SnowflakeId.class));
    }
    
    @Test
    void asFriendlyId() {
        String generatorName = "asFriendlyId";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        DefaultIdGeneratorProvider.INSTANCE.set(generatorName, new DefaultSnowflakeFriendlyId(new MillisecondSnowflakeId(1)));
        assertThat(lazyIdGenerator.asSnowflakeId(false), isA(SnowflakeFriendlyId.class));
    }
    
    @Test
    void asSegmentId() {
        String generatorName = "asSegmentId";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        DefaultIdGeneratorProvider.INSTANCE.set(generatorName, new DefaultSegmentId(new IdSegmentDistributor.Atomic()));
        assertThat(lazyIdGenerator.asSegmentId(false), isA(SegmentId.class));
    }
    
    @Test
    void getActual() {
        String generatorName = "getActual";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        DefaultIdGeneratorProvider.INSTANCE.set(generatorName, MockIdGenerator.INSTANCE);
        assertThat(lazyIdGenerator.getActual(), equalTo(MockIdGenerator.INSTANCE));
    }
    
    @Test
    void idConverter() {
        String generatorName = "idConverter";
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName);
        DefaultIdGeneratorProvider.INSTANCE.set(generatorName, MockIdGenerator.INSTANCE);
        assertThat(lazyIdGenerator.idConverter(), equalTo(MockIdGenerator.INSTANCE.idConverter()));
    }
}
