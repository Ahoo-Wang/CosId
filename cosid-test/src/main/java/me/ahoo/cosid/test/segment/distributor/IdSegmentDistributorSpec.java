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

package me.ahoo.cosid.test.segment.distributor;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;
import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_SEGMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentChain;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IdSegmentDistributorSpec .
 * TODO
 *
 * @author ahoo wang
 */
public abstract class IdSegmentDistributorSpec {
    static final long TEST_OFFSET = 0;
    static final long TEST_STEP = 100;
    
    protected abstract IdSegmentDistributorFactory getFactory();
    
    protected void prepare(IdSegmentDistributor distributor) {
    
    }
    
    @Test
    public void getNamespace() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        String name = "getNamespace";
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, name, TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        assertThat(distributor.getNamespace(), equalTo(namespace));
    }
    
    @Test
    public void getName() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        String name = "getName";
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, name, TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        assertThat(distributor.getName(), equalTo(name));
    }
    
    @Test
    public void getNamespacedName() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        String name = "getNamespacedName";
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, name, TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        String expected = IdSegmentDistributor.getNamespacedName(namespace, name);
        assertThat(distributor.getNamespacedName(), equalTo(expected));
    }
    
    @Test
    public void getStep() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "getStep", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        assertThat(distributor.getStep(), equalTo(TEST_STEP));
    }
    
    @Test
    public void getStepWithSegments() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        int segments = ThreadLocalRandom.current().nextInt();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "getStepWithSegments", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expected = Math.multiplyExact(TEST_STEP, segments);
        long actual = distributor.getStep(segments);
        assertThat(actual, equalTo(expected));
    }
    
    @Test
    public void nextMaxId() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxId", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expected = TEST_OFFSET + TEST_STEP;
        long actual = distributor.nextMaxId();
        assertThat(actual, equalTo(expected));
        
        long actual2 = distributor.nextMaxId();
        assertThat(actual2, greaterThan(actual));
    }
    
    @Test
    public void nextMaxIdWithStep() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        long step = 50;
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdWithStep", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expected = TEST_OFFSET + step;
        long actual = distributor.nextMaxId(step);
        assertThat(actual, equalTo(expected));
    }
    
    protected abstract <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId);
    
    @Test
    public void nextMaxIdWhenBack() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdWhenBack", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expected = TEST_OFFSET + TEST_STEP;
        long actual = distributor.nextMaxId();
        assertThat(actual, equalTo(expected));
        setMaxIdBack(distributor, actual);
        Assert.assertThrows(IllegalStateException.class, distributor::nextMaxId);
    }
    
    @Test
    public void nextIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegment", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expectedMaxId = TEST_OFFSET + TEST_STEP;
        IdSegment actual = distributor.nextIdSegment();
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), equalTo(TIME_TO_LIVE_FOREVER));
    }
    
    @Test
    public void nextIdSegmentWithTtl() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        long ttl = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegmentWithTtl", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expectedMaxId = TEST_OFFSET + TEST_STEP;
        IdSegment actual = distributor.nextIdSegment(ttl);
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), equalTo(ttl));
    }
    
    @Test
    public void nextIdSegmentWithSegmentsAndTtl() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        int segments = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        long ttl = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegmentWithSegmentsAndTtl", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expectedMaxId = TEST_OFFSET + Math.multiplyExact(TEST_STEP, segments);
        long expectedStep = Math.multiplyExact(TEST_STEP, segments);
        IdSegment actual = distributor.nextIdSegment(segments, ttl);
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(expectedStep));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), equalTo(ttl));
    }
    
    @Test
    public void nextIdSegmentChain() {
        IdSegmentChain root = IdSegmentChain.newRoot();
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegmentChain", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        long expectedMaxId = TEST_OFFSET + Math.multiplyExact(TEST_STEP, DEFAULT_SEGMENTS);
        IdSegment actual = distributor.nextIdSegmentChain(root);
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), equalTo(TIME_TO_LIVE_FOREVER));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void nextMaxIdConcurrent() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        int times = 100;
        CompletableFuture<Long>[] results = new CompletableFuture[100];
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdConcurrent", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        for (int i = 0; i < times; i++) {
            results[i] = CompletableFuture.supplyAsync(() -> distributor.nextMaxId(1));
        }
        CompletableFuture.allOf(results).join();
        Long[] machineIds = Arrays.stream(results).map(CompletableFuture::join).sorted().toArray(Long[]::new);
        for (int i = 0; i < machineIds.length; i++) {
            assertThat(machineIds[i], equalTo((long) (i + 1)));
        }
    }
    
    @Test
    public void generateConcurrent() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateConcurrent", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        SegmentId segmentId = new DefaultSegmentId(distributor);
        new ConcurrentGenerateSpec(segmentId).verify();
    }
    
    @Test
    public void generateConcurrentOfChain() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateConcurrentOfChain", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        SegmentChainId segmentId = new SegmentChainId(distributor);
        new ConcurrentGenerateSpec(segmentId).verify();
    }
    
    @Test
    public void generateMultiInstanceConcurrent() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateMultiInstanceConcurrent", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = getFactory().create(definition);
        SegmentId segmentId = new DefaultSegmentId(distributor);
        IdSegmentDistributor distributor2 = getFactory().create(definition);
        SegmentId segmentId2 = new DefaultSegmentId(distributor2);
        new ConcurrentGenerateSpec(segmentId, segmentId2).verify();
    }
    
    //    @Test
    //    public void generateMultiInstanceConcurrentOfChain() {
    //        String namespace = MockIdGenerator.INSTANCE.generateAsString();
    //        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateMultiInstanceConcurrentOfChain", TEST_OFFSET, TEST_STEP);
    //        IdSegmentDistributor distributor = getFactory().create(definition);
    //        SegmentId segmentId = new SegmentChainId(distributor);
    //        IdSegmentDistributor distributor2 = getFactory().create(definition);
    //        SegmentId segmentId2 = new SegmentChainId(distributor2);
    //        new ConcurrentGenerateSpec(segmentId, segmentId2).verify();
    //    }
}
