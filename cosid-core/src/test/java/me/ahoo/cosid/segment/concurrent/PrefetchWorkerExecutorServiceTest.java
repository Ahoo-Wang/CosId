package me.ahoo.cosid.segment.concurrent;

import me.ahoo.cosid.util.MockIdGenerator;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PrefetchWorkerExecutorServiceTest .
 *
 * @author ahoo wang
 */
class PrefetchWorkerExecutorServiceTest {
    PrefetchWorkerExecutorService executorService;
    
    @BeforeEach
    void setup() {
        executorService = new PrefetchWorkerExecutorService(PrefetchWorkerExecutorService.DEFAULT_PREFETCH_PERIOD, 1);
    }
    
    @Test
    void shutdown() {
        executorService.shutdown();
        // Multiple shutdowns have no side effects
        executorService.shutdown();
    }
    
    @Test
    void submit() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        executorService.submit(new AffinityJob() {
            @Override
            public String getJobId() {
                return MockIdGenerator.INSTANCE.generateAsString();
            }
            
            @Override
            public void setHungerTime(long hungerTime) {
            
            }
            
            @Override
            public PrefetchWorker getPrefetchWorker() {
                return null;
            }
            
            @Override
            public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            
            }
            
            @Override
            public void run() {
                countDownLatch.countDown();
            }
        });
    }
    
    @SneakyThrows
    @Test
    void checkBoundThread() {
        CountDownLatch countDownLatch = new CountDownLatch(10);
        executorService.submit(new AffinityJob() {
            private final AtomicReference<Thread> boundThread = new AtomicReference<>();
            
            @Override
            public String getJobId() {
                return MockIdGenerator.INSTANCE.generateAsString();
            }
            
            @Override
            public void setHungerTime(long hungerTime) {
            
            }
            
            @Override
            public PrefetchWorker getPrefetchWorker() {
                return null;
            }
            
            @Override
            public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            
            }
            
            @Override
            public void run() {
                if (!boundThread.compareAndSet(null, Thread.currentThread())) {
                    if (Thread.currentThread().equals(boundThread.get())) {
                        countDownLatch.countDown();
                    }
                }
            }
        });
        Assertions.assertTrue(countDownLatch.await(15, TimeUnit.SECONDS));
    }

}
