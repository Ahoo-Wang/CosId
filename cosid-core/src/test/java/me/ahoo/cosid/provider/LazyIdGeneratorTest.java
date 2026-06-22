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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
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
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        assertThat(lazyIdGenerator.tryGet(false), nullValue());
        idGeneratorProvider.set(generatorName, MockIdGenerator.INSTANCE);
        assertThat(lazyIdGenerator.tryGet(false), equalTo(MockIdGenerator.INSTANCE));
    }
    
    @Test
    void asSnowflakeId() {
        String generatorName = "asSnowflakeId";
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        idGeneratorProvider.set(generatorName, snowflakeId);
        assertThat(lazyIdGenerator.asSnowflakeId(false), equalTo(snowflakeId));
    }
    
    @Test
    void asFriendlyId() {
        String generatorName = "asFriendlyId";
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        SnowflakeFriendlyId friendlyId = new DefaultSnowflakeFriendlyId(new MillisecondSnowflakeId(1));
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        idGeneratorProvider.set(generatorName, friendlyId);
        assertThat(lazyIdGenerator.asFriendlyId(false), equalTo(friendlyId));
    }
    
    @Test
    void asSegmentId() {
        String generatorName = "asSegmentId";
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        SegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Atomic());
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        idGeneratorProvider.set(generatorName, segmentId);
        assertThat(lazyIdGenerator.asSegmentId(false), equalTo(segmentId));
    }
    
    @Test
    void getActual() {
        String generatorName = "getActual";
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        idGeneratorProvider.set(generatorName, MockIdGenerator.INSTANCE);
        assertThat(lazyIdGenerator.getActual(), equalTo(MockIdGenerator.INSTANCE));
    }
    
    @Test
    void idConverter() {
        String generatorName = "idConverter";
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        idGeneratorProvider.set(generatorName, MockIdGenerator.INSTANCE);
        assertThat(lazyIdGenerator.idConverter(), equalTo(MockIdGenerator.INSTANCE.idConverter()));
    }

    @Test
    void tryGetShouldThrowWhenRequiredGeneratorMissing() {
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator("missing", new DefaultIdGeneratorProvider());

        NotFoundIdGeneratorException exception = Assertions.assertThrows(NotFoundIdGeneratorException.class, () -> {
            lazyIdGenerator.tryGet(true);
        });

        assertThat(exception.getGeneratorName(), equalTo("missing"));
        assertThat(exception.getMessage(), equalTo("IdGenerator name:[missing] not found."));
    }

    @Test
    void typedAccessorShouldRejectMismatchedGenerator() {
        String generatorName = "typedAccessorShouldRejectMismatchedGenerator";
        DefaultIdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider();
        IdGenerator generator = MockIdGenerator.INSTANCE;
        LazyIdGenerator lazyIdGenerator = new LazyIdGenerator(generatorName, idGeneratorProvider);
        idGeneratorProvider.set(generatorName, generator);

        CosIdException exception = Assertions.assertThrows(CosIdException.class, () -> {
            lazyIdGenerator.asSegmentId(true);
        });

        assertThat(exception.getMessage(), equalTo("IdGenerator:[typedAccessorShouldRejectMismatchedGenerator] is not instanceof SegmentId!"));
    }
}
