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

package me.ahoo.cosid.test;

import me.ahoo.cosid.IdGenerator;

import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Concurrent Generate String ID Spec .
 *
 * @author ahoo wang
 */
public class ConcurrentGenerateStingSpec implements TestSpec {
    private final IdGenerator[] idGenerators;
    private final int concurrentThreads;
    private final long idSize;
    private final int singleGenerates;

    public ConcurrentGenerateStingSpec(IdGenerator... idGenerators) {
        this(10, 800000, idGenerators);
    }

    public ConcurrentGenerateStingSpec(int concurrentThreads, long idSize, IdGenerator... idGenerators) {
        Preconditions.checkState(idGenerators.length > 0, "idGenerators can not be empty.");
        this.idGenerators = idGenerators;
        this.concurrentThreads = concurrentThreads;
        this.idSize = idSize;
        this.singleGenerates = (int) (idSize / concurrentThreads);
    }

    public int getConcurrentThreads() {
        return concurrentThreads;
    }

    public long getIdSize() {
        return idSize;
    }

    private IdGenerator getIdGenerator(int threadIdx) {
        return idGenerators[threadIdx % (idGenerators.length)];
    }

    protected void assertSingleEach(String previousId, String id) {
        Preconditions.checkState(id.compareTo(previousId) > 0, "id:[%s] must greater then previousId:[%s]", id, previousId);
    }

    protected void assertGlobalEach(String previousId, String id) {
        Preconditions.checkState(id.compareTo(previousId) > 0, "id:[%s] must equals previousId:[%s]+1.", id, previousId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void verify() {

        CompletableFuture<String[]>[] completableFutures = new CompletableFuture[concurrentThreads];

        for (int i = 0; i < completableFutures.length; i++) {
            final IdGenerator idGenerator = getIdGenerator(i);
            completableFutures[i] = CompletableFuture
                    .supplyAsync(() -> {
                        String[] ids = new String[singleGenerates];
                        String previousId = "0";
                        for (int j = 0; j < ids.length; j++) {
                            String nextId = idGenerator.generateAsString();
                            ids[j] = nextId;
                            assertSingleEach(previousId, nextId);
                            previousId = nextId;
                        }
                        return ids;
                    });
        }

        CompletableFuture
                .allOf(completableFutures)
                .thenAccept(nil -> {
                    final String[] totalIds = new String[(int) idSize];
                    int totalIdx = 0;
                    for (CompletableFuture<String[]> completableFuture : completableFutures) {
                        String[] ids = completableFuture.join();
                        for (String id : ids) {
                            totalIds[totalIdx++] = id;
                        }
                    }

                    Arrays.sort(totalIds);

                    String previousId = "-1";
                    for (String id : totalIds) {
                        if ("-1".equals(previousId)) {
                            previousId = id;
                            continue;
                        }
                        assertGlobalEach(previousId, id);
                        previousId = id;
                    }
                }).join();
    }
}
