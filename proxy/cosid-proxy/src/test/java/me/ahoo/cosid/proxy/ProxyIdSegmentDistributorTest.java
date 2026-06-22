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

package me.ahoo.cosid.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.ahoo.cosid.proxy.api.SegmentClient;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;

import org.junit.jupiter.api.Test;

class ProxyIdSegmentDistributorTest {

    @Test
    void factoryCreatesRemoteDistributorBeforeReturningProxy() {
        RecordingSegmentClient segmentClient = new RecordingSegmentClient();
        IdSegmentDistributorFactory factory = new ProxyIdSegmentDistributorFactory(segmentClient);

        IdSegmentDistributor distributor = factory.create(new IdSegmentDistributorDefinition("test_namespace", "order", 100, 20));

        assertEquals("test_namespace", segmentClient.createdNamespace);
        assertEquals("order", segmentClient.createdName);
        assertEquals(100, segmentClient.createdOffset);
        assertEquals(20, segmentClient.createdStep);
        assertEquals("test_namespace", distributor.getNamespace());
        assertEquals("order", distributor.getName());
        assertEquals(20, distributor.getStep());
    }

    @Test
    void nextMaxIdDelegatesToSegmentClientWithRuntimeStep() {
        RecordingSegmentClient segmentClient = new RecordingSegmentClient();
        ProxyIdSegmentDistributor distributor = new ProxyIdSegmentDistributor(segmentClient, "test_namespace", "order", 20);

        long nextMaxId = distributor.nextMaxId(5);

        assertEquals(205, nextMaxId);
        assertEquals("test_namespace", segmentClient.nextNamespace);
        assertEquals("order", segmentClient.nextName);
        assertEquals(5, segmentClient.nextStep);
    }

    private static class RecordingSegmentClient implements SegmentClient {
        private String createdNamespace;
        private String createdName;
        private long createdOffset;
        private long createdStep;
        private String nextNamespace;
        private String nextName;
        private long nextStep;

        @Override
        public void createDistributor(String namespace, String name, long offset, long step) {
            this.createdNamespace = namespace;
            this.createdName = name;
            this.createdOffset = offset;
            this.createdStep = step;
        }

        @Override
        public long nextMaxId(String namespace, String name, long step) {
            this.nextNamespace = namespace;
            this.nextName = name;
            this.nextStep = step;
            return 200 + step;
        }
    }
}
