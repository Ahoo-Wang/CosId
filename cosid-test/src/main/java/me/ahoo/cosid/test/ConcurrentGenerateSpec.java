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

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent Generate Spec .
 *
 * @author ahoo wang
 */
public class ConcurrentGenerateSpec implements TestSpec {
    public static final int DEFAULT_CONCURRENT_THREADS = 4;
    public static final long DEFAULT_ID_SIZE = 4000;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int ORIGIN_LIMIT = 5;

    private final IdGenerator[] idGenerators;
    private final int concurrentThreads;
    private final long idSize;
    private final int singleGenerates;
    private final Duration timeout;

    public ConcurrentGenerateSpec(IdGenerator... idGenerators) {
        this(DEFAULT_CONCURRENT_THREADS, DEFAULT_ID_SIZE, idGenerators);
    }

    public ConcurrentGenerateSpec(int concurrentThreads, long idSize, IdGenerator... idGenerators) {
        this(concurrentThreads, idSize, DEFAULT_TIMEOUT, idGenerators);
    }

    public ConcurrentGenerateSpec(int concurrentThreads, long idSize, Duration timeout, IdGenerator... idGenerators) {
        Preconditions.checkState(idGenerators.length > 0, "idGenerators can not be empty.");
        Preconditions.checkState(concurrentThreads > 0, "concurrentThreads:[%s] must greater than 0.", concurrentThreads);
        Preconditions.checkState(idSize > 0, "idSize:[%s] must greater than 0.", idSize);
        Preconditions.checkState(idSize <= Integer.MAX_VALUE, "idSize:[%s] must less than Integer.MAX_VALUE.", idSize);
        Preconditions.checkState(idSize % concurrentThreads == 0, "idSize:[%s] must be divisible by concurrentThreads:[%s].", idSize, concurrentThreads);
        this.timeout = Preconditions.checkNotNull(timeout, "timeout can not be null.");
        Preconditions.checkState(timeout.toMillis() > 0, "timeout:[%s] must greater than 0 millis.", timeout);
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
        return idGenerators[getGeneratorIndex(threadIdx)];
    }

    private int getGeneratorIndex(int threadIdx) {
        return threadIdx % idGenerators.length;
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
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads, newThreadFactory());
        CompletableFuture<GeneratedBatch>[] futures = new CompletableFuture[concurrentThreads];

        try {
            for (int i = 0; i < futures.length; i++) {
                int workerIndex = i;
                int generatorIndex = getGeneratorIndex(i);
                IdGenerator idGenerator = getIdGenerator(i);
                futures[i] = CompletableFuture.supplyAsync(() -> generateBatch(workerIndex, generatorIndex, idGenerator), executor);
            }
            waitForAll(futures);
            assertGlobal(futures);
        } finally {
            shutdown(executor);
        }
    }

    private ThreadFactory newThreadFactory() {
        AtomicInteger threadIndex = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "cosid-concurrent-generate-" + threadIndex.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private GeneratedBatch generateBatch(int workerIndex, int generatorIndex, IdGenerator idGenerator) {
        long[] ids = new long[singleGenerates];
        long previousId = 0;
        for (int i = 0; i < ids.length; i++) {
            long nextId = idGenerator.generate();
            ids[i] = nextId;
            try {
                assertSingleEach(previousId, nextId);
            } catch (RuntimeException | AssertionError error) {
                throw new AssertionError("Single ID order violation - " + specContext()
                    + ",workerIndex:" + workerIndex + ",generatorIndex:" + generatorIndex + ",localIndex:" + i
                    + ",previousId:" + previousId + ",id:" + nextId, error);
            }
            previousId = nextId;
        }
        return new GeneratedBatch(workerIndex, generatorIndex, ids);
    }

    private void waitForAll(CompletableFuture<GeneratedBatch>[] futures) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        try {
            all.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            Arrays.stream(futures).forEach(future -> future.cancel(true));
            throw new AssertionError("Concurrent ID generation timed out - " + specContext(), timeoutException);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent ID generation interrupted - " + specContext(), interruptedException);
        } catch (ExecutionException executionException) {
            Throwable cause = unwrap(executionException);
            if (cause instanceof AssertionError assertionError) {
                throw assertionError;
            }
            throw new AssertionError("Concurrent ID generation failed - " + specContext(), cause);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable.getCause();
        if ((throwable instanceof ExecutionException || throwable instanceof CompletionException) && cause != null) {
            return unwrap(cause);
        }
        return throwable;
    }

    private void assertGlobal(CompletableFuture<GeneratedBatch>[] futures) {
        GeneratedBatch[] batches = Arrays.stream(futures)
            .map(CompletableFuture::join)
            .toArray(GeneratedBatch[]::new);
        long[] totalIds = new long[(int) idSize];
        int totalIdx = 0;
        for (GeneratedBatch batch : batches) {
            for (long id : batch.ids) {
                totalIds[totalIdx++] = id;
            }
        }

        Arrays.sort(totalIds);

        long previousId = -1;
        for (int i = 0; i < totalIds.length; i++) {
            long id = totalIds[i];
            if (-1 == previousId) {
                try {
                    assertGlobalFirst(id);
                } catch (RuntimeException | AssertionError error) {
                    throw globalAssertionError("Global first ID violation", error, i, previousId, id, totalIds, batches);
                }
                previousId = id;
                continue;
            }
            try {
                assertGlobalEach(previousId, id);
            } catch (RuntimeException | AssertionError error) {
                throw globalAssertionError("Global ID sequence violation", error, i, previousId, id, totalIds, batches);
            }
            previousId = id;
        }
        try {
            assertGlobalLast(previousId);
        } catch (RuntimeException | AssertionError error) {
            throw globalAssertionError("Global last ID violation", error, totalIds.length - 1, previousId, previousId, totalIds, batches);
        }
    }

    private AssertionError globalAssertionError(String message, Throwable cause, int sortedIndex, long previousId, long id, long[] totalIds,
                                                GeneratedBatch[] batches) {
        return new AssertionError(message + " - " + specContext()
            + ",sortedIndex:" + sortedIndex + ",previousId:" + previousId + ",id:" + id
            + ",relation:" + relation(previousId, id)
            + ",duplicateCount:" + countOccurrences(id, totalIds)
            + ",window:" + sampleWindow(totalIds, sortedIndex)
            + ",origins:" + describeOrigins(id, batches), cause);
    }

    private String relation(long previousId, long id) {
        if (previousId == id) {
            return "duplicate";
        }
        if (previousId > id) {
            return "non-increasing";
        }
        if (previousId + 1 < id) {
            return "gap";
        }
        return "assertion-failed";
    }

    private int countOccurrences(long id, long[] totalIds) {
        int count = 0;
        for (long totalId : totalIds) {
            if (totalId == id) {
                count++;
            }
        }
        return count;
    }

    private String sampleWindow(long[] totalIds, int sortedIndex) {
        int from = Math.max(0, sortedIndex - 3);
        int to = Math.min(totalIds.length, sortedIndex + 4);
        return Arrays.toString(Arrays.copyOfRange(totalIds, from, to));
    }

    private String describeOrigins(long id, GeneratedBatch[] batches) {
        StringBuilder origins = new StringBuilder("[");
        int count = 0;
        for (GeneratedBatch batch : batches) {
            for (int i = 0; i < batch.ids.length; i++) {
                if (batch.ids[i] != id) {
                    continue;
                }
                if (count > 0) {
                    origins.append(',');
                }
                if (count < ORIGIN_LIMIT) {
                    origins.append("{workerIndex:").append(batch.workerIndex)
                        .append(",generatorIndex:").append(batch.generatorIndex)
                        .append(",localIndex:").append(i).append('}');
                }
                count++;
            }
        }
        if (count > ORIGIN_LIMIT) {
            origins.append(",... total:").append(count);
        }
        return origins.append(']').toString();
    }

    private String specContext() {
        return "sampleSize:" + idSize + ",concurrentThreads:" + concurrentThreads + ",singleGenerates:" + singleGenerates
            + ",generators:" + idGenerators.length + ",timeout:" + timeout;
    }

    private void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static final class GeneratedBatch {
        private final int workerIndex;
        private final int generatorIndex;
        private final long[] ids;

        private GeneratedBatch(int workerIndex, int generatorIndex, long[] ids) {
            this.workerIndex = workerIndex;
            this.generatorIndex = generatorIndex;
            this.ids = ids;
        }
    }
}
