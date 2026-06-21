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

import com.mongodb.MongoClientSettings;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MachineOperatesTest {

    @Test
    void distributeBySelfUpdateShouldNotLowerLastTimestamp() {
        assertMaxLastTimestamp(MachineOperates.distributeBySelfUpdate(100), 100);
    }

    @Test
    void distributeByRevertUpdateShouldNotLowerLastTimestamp() {
        Bson update = MachineOperates.distributeByRevertUpdate(InstanceId.of("host", 1, false), 100);

        assertMaxLastTimestamp(update, 100);
    }

    @Test
    void revertUpdateShouldNotLowerLastTimestamp() {
        Bson update = MachineOperates.revertUpdate(InstanceId.of("host", 1, false), MachineState.of(1, 100));

        assertMaxLastTimestamp(update, 100);
    }

    @Test
    void guardUpdateShouldNotLowerLastTimestamp() {
        assertMaxLastTimestamp(MachineOperates.guardUpdate(100), 100);
    }

    private void assertMaxLastTimestamp(Bson update, long lastTimestamp) {
        BsonDocument updateDocument = update.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
        BsonDocument maxDocument = updateDocument.getDocument("$max", null);
        Assertions.assertNotNull(maxDocument);
        Assertions.assertEquals(lastTimestamp, maxDocument.getInt64(MachineOperates.LAST_TIMESTAMP_FIELD).longValue());
        BsonDocument setDocument = updateDocument.getDocument("$set", new BsonDocument());
        Assertions.assertFalse(setDocument.containsKey(MachineOperates.LAST_TIMESTAMP_FIELD));
    }
}
