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

package me.ahoo.cosid.sharding;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
class ExactCollectionTest {

    public ExactCollection<String> createExactCollection(int size) {
        return new ExactCollection<>(size);
    }

    @Test
    public void ctor() {
        ExactCollection<String> exactCollection = createExactCollection(10);
        Assertions.assertNotNull(exactCollection);
        Assertions.assertEquals(10, exactCollection.size());
    }

    @Test
    public void add() {
        ExactCollection<String> exactCollection = new ExactCollection<>(10);
        for (int i = 0; i < exactCollection.size(); i++) {
            exactCollection.add(i, String.valueOf(i));
        }
        Assertions.assertNotNull(exactCollection);
        Assertions.assertEquals(10, exactCollection.size());
    }

    @Test
    public void iterator() {
        ExactCollection<String> exactCollection = new ExactCollection<>(10);
        for (int i = 0; i < exactCollection.size(); i++) {
            exactCollection.add(i, String.valueOf(i));
        }
        Assertions.assertEquals(10, exactCollection.size());
        int idx = 0;
        for (String element : exactCollection) {
            Assertions.assertEquals(String.valueOf(idx), element);
            idx++;
        }
    }

    @Test
    public void eq() {
        ExactCollection<String> exactCollection = new ExactCollection<>(2);
        exactCollection.add(0, "0");
        exactCollection.add(1, "1");
        Assertions.assertEquals(new ExactCollection<String>("0", "1"), exactCollection);
        Assertions.assertEquals(new ExactCollection<String>("1", "0"), exactCollection);
        Assertions.assertNotEquals(new ExactCollection<String>("0"), exactCollection);
        Assertions.assertNotEquals(new ExactCollection<String>("0", "2"), exactCollection);
    }

    @Test
    void toArray() {
        ExactCollection<String> exactCollection = new ExactCollection<>(10);
        exactCollection.add(0, "0");
        exactCollection.add(1, "1");
        Assertions.assertEquals(exactCollection.toArray()[0], exactCollection.get(0));
    }


    @Test
    void remove() {
        ExactCollection<String> exactCollection = new ExactCollection<>(10);
        exactCollection.add(0, "0");
        exactCollection.add(1, "1");
        exactCollection.remove("1");
        Assertions.assertTrue(exactCollection.stream().filter(x -> Objects.nonNull(x)).collect(Collectors.toList()).size() == 1);
    }

    @Test
    void addAll() {
        ExactCollection<String> exactCollection = new ExactCollection<>(10);
        exactCollection.add(0, "0");
        exactCollection.add(1, "1");
        Collection<String> addCollection = new ArrayList<>();
        addCollection.add("3");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            exactCollection.addAll(addCollection);
        });
    }

    @Test
    void removeAll() {
    }

    @Test
    void retainAll() {
    }
}
