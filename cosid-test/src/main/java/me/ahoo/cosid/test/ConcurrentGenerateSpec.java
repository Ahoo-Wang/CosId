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
 * Concurrent Generate Spec .
 *
 * @author ahoo wang
 */
public class ConcurrentGenerateSpec implements TestSpec {
    private final IdGenerator[] idGenerators;
    private final int concurrentThreads;
    private final long idSize;
    private final int singleGenerates;
    
    public ConcurrentGenerateSpec(IdGenerator... idGenerators) {
        this(10, 800000, idGenerators);
    }
    
    public ConcurrentGenerateSpec(int concurrentThreads, long idSize, IdGenerator... idGenerators) {
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
    
    protected void assertSingleEach(long previousId, long id) {
        Preconditions.checkState(id > previousId, "id:[%s] must greater then previousId:[%s]", id, previousId);
    }
    
    protected void assertGlobalFirst(long id) {
        Preconditions.checkState(1 == id, "id:[%s] must equals 1.", id);
    }
    
    protected void assertGlobalEach(long previousId, long id) {
        Preconditions.checkState(id == previousId + 1, "id:[%s] must equals previousId:[%s]+1.", id, previousId);
    }
    
    protected void assertGlobalLast(long lastId) {
        Preconditions.checkState(getIdSize() == lastId, "lastId:[%s] must equals idSize:[%s]", lastId, getIdSize());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void verify() {
        
        CompletableFuture<long[]>[] completableFutures = new CompletableFuture[concurrentThreads];
        
        for (int i = 0; i < completableFutures.length; i++) {
            final IdGenerator idGenerator = getIdGenerator(i);
            completableFutures[i] = CompletableFuture
                .supplyAsync(() -> {
                    long[] ids = new long[singleGenerates];
                    long previousId = 0;
                    for (int j = 0; j < ids.length; j++) {
                        long nextId = idGenerator.generate();
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
                final long[] totalIds = new long[(int) idSize];
                int totalIdx = 0;
                for (CompletableFuture<long[]> completableFuture : completableFutures) {
                    long[] ids = completableFuture.join();
                    for (long id : ids) {
                        totalIds[totalIdx++] = id;
                    }
                }
                
                Arrays.sort(totalIds);
                
                long previousId = -1;
                for (long id : totalIds) {
                    if (-1 == previousId) {
                        assertGlobalFirst(id);
                        previousId = id;
                        continue;
                    }
                    assertGlobalEach(previousId, id);
                    previousId = id;
                }
                assertGlobalLast(previousId);
            }).join();
    }
}
