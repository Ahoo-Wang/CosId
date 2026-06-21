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

package me.ahoo.cosid.mongo;

import me.ahoo.cosid.segment.IdSegmentDistributor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MongoIdSegmentDistributorUnitTest {

    @Test
    void nextMaxIdShouldPassOffsetToCollection() {
        RecordingIdSegmentCollection collection = new RecordingIdSegmentCollection();
        MongoIdSegmentDistributor distributor = new MongoIdSegmentDistributor("ns", "name", 7, 100, collection);

        long actual = distributor.nextMaxId();

        Assertions.assertEquals(107, actual);
        Assertions.assertEquals(IdSegmentDistributor.getNamespacedName("ns", "name"), collection.namespacedName);
        Assertions.assertEquals(7, collection.offset);
        Assertions.assertEquals(100, collection.step);
    }

    @Test
    void nextMaxIdShouldRejectNonPositiveStep() {
        MongoIdSegmentDistributor distributor = new MongoIdSegmentDistributor("ns", "name", 0, 100, new RecordingIdSegmentCollection());

        Assertions.assertThrows(IllegalArgumentException.class, () -> distributor.nextMaxId(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> distributor.nextMaxId(-1));
    }

    @Test
    void constructorShouldRejectInvalidOffsetAndStep() {
        RecordingIdSegmentCollection collection = new RecordingIdSegmentCollection();

        Assertions.assertThrows(IllegalArgumentException.class, () -> new MongoIdSegmentDistributor("ns", "name", -1, 100, collection));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new MongoIdSegmentDistributor("ns", "name", 0, 0, collection));
    }

    private static final class RecordingIdSegmentCollection implements IdSegmentCollection {
        private String namespacedName;
        private long offset;
        private long step;

        @Override
        public long incrementAndGet(String namespacedName, long offset, long step) {
            this.namespacedName = namespacedName;
            this.offset = offset;
            this.step = step;
            return offset + step;
        }
    }
}
