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

package me.ahoo.cosid.server;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.store.remote.RemoteApi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The optional id endpoints (08-remote.md §2, {@code cosid.server.ids.enabled}): the generators of the server's
 * {@link CosIdRuntime}, for clients that cannot reach a store. Without a generator in the path they use
 * {@value RuntimeSpec#DEFAULT_GENERATOR} (the 4.x share). A client token needs the runtime's namespace.
 */
@RestController
@ConditionalOnProperty(prefix = ServerProperties.PREFIX + ".ids", name = "enabled", havingValue = "true")
@Tag(name = "ids", description = "Ids from the generators of the server's runtime (optional)")
class IdController {
    private final CosIdRuntime runtime;
    private final Requests requests;
    private final BlockingCalls calls;

    /** Path segments of the id endpoints, which a generator name would be shadowed by. */
    static final Set<String> RESERVED_NAMES = Set.of("strings", "periodic");

    IdController(CosIdRuntime runtime, Requests requests, BlockingCalls calls) {
        List<String> reserved = runtime.describe().keySet().stream().filter(RESERVED_NAMES::contains).toList();
        if (!reserved.isEmpty()) {
            throw new IllegalStateException(ServerProperties.PREFIX + ".ids.enabled: rename the generators " + reserved
                + "; " + RESERVED_NAMES + " are paths of the id endpoints (" + RemoteApi.IDS + "/strings would shadow a generator named strings)");
        }
        this.runtime = runtime;
        this.requests = requests;
        this.calls = calls;
    }

    @GetMapping(RemoteApi.IDS)
    @Operation(summary = "Ids of the default generator")
    CompletableFuture<Bodies.IdsBody> defaultIds(@RequestParam(defaultValue = "1") int count, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        return ids(RuntimeSpec.DEFAULT_GENERATOR, count, grant);
    }

    @GetMapping(RemoteApi.IDS + "/strings")
    @Operation(summary = "Ids of the default generator, encoded by its codec")
    CompletableFuture<Bodies.StringIdsBody> defaultStrings(@RequestParam(defaultValue = "1") int count, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        return strings(RuntimeSpec.DEFAULT_GENERATOR, count, grant);
    }

    @GetMapping(RemoteApi.IDS + "/{generator}")
    @Operation(summary = "Ids of a generator")
    CompletableFuture<Bodies.IdsBody> ids(@PathVariable String generator, @RequestParam(defaultValue = "1") int count,
                                          @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        int n = admit(count, grant);
        IdGenerator ids = lookup(generator, () -> runtime.generator(generator));
        return calls.submit(() -> {
            List<Long> issued = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                issued.add(ids.generate());
            }
            return new Bodies.IdsBody(issued);
        });
    }

    @GetMapping(RemoteApi.IDS + "/{generator}/strings")
    @Operation(summary = "Text ids of a generator, of any strategy (its codec, the 80-bit or periodic text, UUIDv7)")
    CompletableFuture<Bodies.StringIdsBody> strings(@PathVariable String generator, @RequestParam(defaultValue = "1") int count,
                                                    @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        int n = admit(count, grant);
        // The text of every strategy (CosIdRuntime.text): cosid80, periodic segments and uuidv7 too, not only long ids.
        Supplier<String> ids = lookup(generator, () -> runtime.text(generator));
        return calls.submit(() -> {
            List<String> issued = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                issued.add(ids.get());
            }
            return new Bodies.StringIdsBody(issued);
        });
    }

    /**
     * Values of a periodic segment generator, all of one period: when the period turns while the values are taken, the
     * answer stops at the turn (fewer than {@code count}; the value of the new period is skipped, a gap).
     */
    @GetMapping(RemoteApi.IDS + "/{generator}/periodic")
    @Operation(summary = "Values of a periodic segment generator, all of one period")
    CompletableFuture<Bodies.PeriodicBody> periodic(@PathVariable String generator, @RequestParam(defaultValue = "1") int count,
                                                    @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        int n = admit(count, grant);
        PeriodicSegmentGenerator ids = lookup(generator, () -> runtime.periodic(generator));
        return calls.submit(() -> {
            GroupedId first = ids.generate();
            List<Long> values = new ArrayList<>(n);
            values.add(first.value());
            for (int i = 1; i < n; i++) {
                GroupedId next = ids.generate();
                if (!next.period().equals(first.period())) {
                    break;
                }
                values.add(next.value());
            }
            return new Bodies.PeriodicBody(first.period(), values);
        });
    }

    private int admit(int count, Grant grant) {
        int n = requests.count(count);
        if (!grant.allowsNamespace(runtime.namespace())) {
            throw ApiException.forbidden("[" + grant.name() + "] may not use the namespace [" + runtime.namespace() + "]");
        }
        return n;
    }

    private static <T> T lookup(String generator, Supplier<T> lookup) {
        try {
            return lookup.get();
        } catch (NoSuchElementException e) {
            throw ApiException.notFound("No such generator [" + generator + "] for this endpoint");
        }
    }
}
