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

package me.ahoo.cosid.provider;

import me.ahoo.cosid.jvm.AtomicLongGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ahoo wang
 */
class DefaultIdGeneratorProviderTest {

    @Test
    void getShare() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        assertNull(provider.getShare());
        provider.setShare(AtomicLongGenerator.INSTANCE);
        assertEquals(AtomicLongGenerator.INSTANCE, provider.getShare());
    }

    @Test
    void setShare() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(AtomicLongGenerator.INSTANCE);
        assertEquals(AtomicLongGenerator.INSTANCE, provider.getShare());
    }

    @Test
    void removeShare() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        assertNull(provider.removeShare());
        provider.setShare(AtomicLongGenerator.INSTANCE);
        assertEquals(AtomicLongGenerator.INSTANCE, provider.getShare());
    }

    @Test
    void get() {
        String idName = "test-get";
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        assertFalse(provider.get(idName).isPresent());
        provider.set(idName, AtomicLongGenerator.INSTANCE);
        assertEquals(AtomicLongGenerator.INSTANCE, provider.get(idName).get());
    }

    @Test
    void remove() {
        String idName = "test-get";
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        assertFalse(provider.get(idName).isPresent());
        provider.set(idName, AtomicLongGenerator.INSTANCE);
        assertTrue(provider.get(idName).isPresent());
        assertEquals(AtomicLongGenerator.INSTANCE, provider.remove(idName));
        assertFalse(provider.get(idName).isPresent());
    }

    @Test
    void set() {

    }

    @Test
    void getOrCreate() {
    }

    @Test
    void clear() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.set("idGen-1", AtomicLongGenerator.INSTANCE);
        provider.set("idGen-2", AtomicLongGenerator.INSTANCE);
        provider.set("idGen-3", AtomicLongGenerator.INSTANCE);
        assertEquals(3, provider.getAll().size());
        provider.clear();
        assertEquals(0, provider.getAll().size());
    }

    @Test
    void getAll() {
    }
}
