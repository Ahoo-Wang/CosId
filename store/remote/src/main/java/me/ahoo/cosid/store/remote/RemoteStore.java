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

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * The remote store (06-stores.md §2.5, 08-remote.md): both primitives as calls to {@code cosid-server}, which runs
 * them on its own backing store. One round trip per call over the JDK {@link java.net.http.HttpClient}; no third-party
 * dependency.
 *
 * <p>Errors the primitives have an outcome for map to it ({@code 410} → {@link me.ahoo.cosid.RangeSealedException},
 * {@code 409 slots_exhausted} → {@link me.ahoo.cosid.lease.AcquireResult.Exhausted}, {@code 409 lease_lost} → an empty
 * renew or a false release, ...); every other failure throws a {@link RemoteStoreException}.
 */
public final class RemoteStore implements Store {
    /** The connect timeout when none is configured. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RemoteClient client;
    private final RemoteRangeStore ranges;
    private final RemoteSlotLeaseStore slots;

    /**
     * A store on a server.
     *
     * @param baseUrl        the server, such as {@code http://cosid-server:8080}
     * @param token          the bearer token; empty for a server that allows anonymous calls
     * @param connectTimeout the connect timeout
     * @param requestTimeout the timeout of each call
     */
    public RemoteStore(URI baseUrl, Optional<String> token, Duration connectTimeout, Duration requestTimeout) {
        this.client = new RemoteClient(baseUrl, token, connectTimeout, requestTimeout);
        this.ranges = new RemoteRangeStore(client);
        this.slots = new RemoteSlotLeaseStore(client);
    }

    /**
     * The server.
     *
     * @return the base URL, without a trailing slash
     */
    public URI baseUrl() {
        return client.baseUrl();
    }

    @Override
    public RangeStore ranges() {
        return ranges;
    }

    @Override
    public SlotLeaseStore slots() {
        return slots;
    }

    /** Closes the HTTP client. */
    @Override
    public void close() {
        client.close();
    }
}
