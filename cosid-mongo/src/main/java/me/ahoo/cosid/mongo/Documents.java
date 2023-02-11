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

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

public interface Documents {
    String ID_FIELD = "_id";
    //region IdSegment
    String LAST_MAX_ID_FIELD = "lastMaxId";
    String LAST_FETCH_TIME_FIELD = "lastFetchTime";
    //endregion
    //region Machine
    String NAMESPACE_FIELD = "namespace";
    String MACHINE_ID_FIELD = "machineId";
    String LAST_TIMESTAMP_FIELD = "lastTimestamp";
    String INSTANCE_ID_FIELD = "instanceId";
    String DISTRIBUTE_TIME_FIELD = "distributeTime";
    String REVERT_TIME_FIELD = "revertTime";
    String MAX_MACHINE_ID_FIELD = "maxMachineId";
    //endregion
    
    FindOneAndUpdateOptions UPDATE_AFTER_OPTIONS = new FindOneAndUpdateOptions()
        .returnDocument(ReturnDocument.AFTER);
}
