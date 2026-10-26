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

package me.ahoo.cosid.segment;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Generating on virtual threads with a store that blocks must not pin their carrier threads: on JDK 21 to 23 a virtual
 * thread that blocks inside {@code synchronized} keeps its carrier (JDK 24 lifted it, JEP 491). Reads the
 * {@code jdk.VirtualThreadPinned} events of JFR and, when the JVM runs with {@code -Djdk.tracePinnedThreads}, the traces
 * it prints; skipped below JDK 21.
 */
class SegmentVirtualThreadPinningTest {
    private static final String PINNED_EVENT = "jdk.VirtualThreadPinned";
    private static final int THREADS = 32;
    private static final int IDS_PER_THREAD = 50;

    @TempDir
    Path recordingDir;

    @Test
    void segmentIdDoesNotPinWhileFetching() throws Exception {
        assertNoPinning(new DefaultSegmentId(new BlockingDistributor()));
    }

    @Test
    void segmentChainIdDoesNotPinWhileFetching() throws Exception {
        // Without a running prefetch worker, the generating threads append to the chain themselves.
        PrefetchWorkerExecutorService stopped = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        stopped.shutdown();
        assertNoPinning(new SegmentChainId(IdSegment.TIME_TO_LIVE_FOREVER, 1, new BlockingDistributor(), stopped));
    }

    private void assertNoPinning(IdGenerator idGenerator) throws Exception {
        Assumptions.assumeTrue(Runtime.version().feature() >= 21, "virtual threads need JDK 21");
        List<String> pinnedStacks;
        // The JDK prints the pinned traces to System.out without a line end: under Gradle's per-thread line buffering
        // they would be lost with the virtual thread, so they go to a stream of this test meanwhile.
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        PrintStream out = System.out;
        try (Recording recording = new Recording()) {
            recording.enable(PINNED_EVENT).withThreshold(Duration.ZERO).withStackTrace();
            recording.start();
            if (System.getProperty("jdk.tracePinnedThreads") != null) {
                System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
            }
            try {
                generateOnVirtualThreads(idGenerator);
            } finally {
                System.setOut(out);
            }
            recording.stop();
            Path dump = recordingDir.resolve("pinning.jfr");
            recording.dump(dump);
            pinnedStacks = RecordingFile.readAllEvents(dump).stream()
                .filter(event -> PINNED_EVENT.equals(event.getEventType().getName()))
                .map(SegmentVirtualThreadPinningTest::stackOf)
                .collect(Collectors.toList());
        }
        Assertions.assertEquals(List.of(), pinnedStacks, "virtual threads were pinned");
        String traces = printed.toString(StandardCharsets.UTF_8);
        Assertions.assertFalse(traces.contains("<== monitors"), () -> "virtual threads were pinned:\n" + traces);
    }

    private static void generateOnVirtualThreads(IdGenerator idGenerator) throws Exception {
        ExecutorService virtualThreads = (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        try {
            List<Future<?>> generations = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                generations.add(virtualThreads.submit(() -> {
                    for (int i = 0; i < IDS_PER_THREAD; i++) {
                        idGenerator.generate();
                    }
                }));
            }
            for (Future<?> generation : generations) {
                generation.get(30, TimeUnit.SECONDS);
            }
        } finally {
            virtualThreads.shutdownNow();
        }
    }

    private static String stackOf(RecordedEvent event) {
        if (event.getStackTrace() == null) {
            return "<no stack>";
        }
        return event.getStackTrace().getFrames().stream()
            .limit(40)
            .map(RecordedFrame::getMethod)
            .map(method -> method.getType().getName() + "." + method.getName())
            .collect(Collectors.joining(" < "));
    }

    /**
     * A store whose every call blocks for a millisecond, like a network round trip.
     */
    private static final class BlockingDistributor implements IdSegmentDistributor {
        private final AtomicLong maxId = new AtomicLong();

        @Override
        public @NonNull String getNamespace() {
            return "pinning";
        }

        @Override
        public @NonNull String getName() {
            return "blocking";
        }

        @Override
        public long getStep() {
            return 10;
        }

        @Override
        public long nextMaxId(long step) {
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return maxId.addAndGet(step);
        }
    }
}
