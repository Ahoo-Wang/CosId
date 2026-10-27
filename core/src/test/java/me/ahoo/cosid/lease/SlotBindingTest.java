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

package me.ahoo.cosid.lease;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class SlotBindingTest {
    @Test
    void raiseCeilingNeverLowersIt() {
        SlotBinding binding = new SlotBinding(1, 100, 200, 1);
        binding.raiseCeiling(150);
        assertThat(binding.ceiling(), is(200L));
        binding.raiseCeiling(300);
        assertThat(binding.ceiling(), is(300L));
        binding.raiseCeiling(300);
        assertThat(binding.ceiling(), is(300L));
    }

    @Test
    void concurrentRaisesKeepTheMaximum() throws InterruptedException {
        SlotBinding binding = new SlotBinding(1, 0, 0, 1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int offset = t;
            threads.add(Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 10_000; i++) {
                    binding.raiseCeiling(i * 8L + offset);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(binding.ceiling(), is(9_999 * 8L + 7));
    }
}
