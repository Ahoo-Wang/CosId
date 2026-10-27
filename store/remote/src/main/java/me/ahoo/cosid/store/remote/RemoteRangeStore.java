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

package me.ahoo.cosid.store.remote;

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link RangeStore} over {@code POST /ranges:reserve} and {@code /ranges:seal}: the backing store of the
 * server reserves, in one round trip.
 */
final class RemoteRangeStore implements RangeStore {
    private final RemoteClient client;

    RemoteRangeStore(RemoteClient client) {
        this.client = client;
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("size", size);
        body.put("initialOffset", initialOffset);
        RemoteClient.Response response = client.post(RemoteApi.RESERVE, body);
        if (response.status() == 200) {
            return new Range(response.longField("start"), response.longField("end"));
        }
        if (RemoteApi.RANGE_SEALED.equals(response.code())) {
            throw new RangeSealedException(key);
        }
        if (RemoteApi.RANGE_EXHAUSTED.equals(response.code())) {
            throw new RangeExhaustedException(key);
        }
        throw response.error("reserve [" + key + "]");
    }

    @Override
    public boolean seal(String key) {
        RemoteClient.Response response = client.post(RemoteApi.SEAL, Map.of("key", key));
        if (response.status() == 204 || response.status() == 200) {
            return true;
        }
        if (RemoteApi.SEAL_UNSUPPORTED.equals(response.code())) {
            return false;
        }
        throw response.error("seal [" + key + "]");
    }
}
