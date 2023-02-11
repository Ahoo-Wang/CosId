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

import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

public interface IdSegmentOperates {
    String LAST_MAX_ID_FIELD = "lastMaxId";
    String LAST_FETCH_TIME_FIELD = "lastFetchTime";
    
    static Bson incrementAndGetUpdates(long step) {
        return Updates.combine(
            Updates.inc(LAST_MAX_ID_FIELD, step),
            Updates.set(LAST_FETCH_TIME_FIELD, System.currentTimeMillis())
        );
    }
    
    static Document ensureIdSegmentDocument(String segmentName, long offset) {
        return new Document()
            .append(Documents.ID_FIELD, segmentName)
            .append(LAST_MAX_ID_FIELD, offset)
            .append(LAST_FETCH_TIME_FIELD, 0L);
    }
}
