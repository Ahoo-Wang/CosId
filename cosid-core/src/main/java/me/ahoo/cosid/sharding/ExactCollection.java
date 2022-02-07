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

import com.google.common.collect.Sets;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/**
 * 准确式集合.
 * 用于 Interval 算法、Mod 算法可以提前预知节点数量的场景。
 * <pre>
 * 主要针对以下问题：
 * -- 使用{@link java.util.HashSet}导致的内存空间浪费
 * -- 添加元素时导致的集合膨胀(也可以通过给定 expectedSize 计算准确 capacity 就像 {@link Sets#newHashSetWithExpectedSize(int)})
 * </pre>
 *
 * @author ahoo wang
 */
public class ExactCollection<E> extends AbstractCollection<E> implements RandomAccess {

    @SuppressWarnings("rawtypes")
    private static final ExactCollection EMPTY = new ExactCollection(0);

    @SuppressWarnings("unchecked")
    public static final <E> ExactCollection<E> empty() {
        return (ExactCollection<E>) EMPTY;
    }

    private final int size;
    private final Object[] elements;

    public ExactCollection(int size) {
        this.size = size;
        this.elements = new Object[size];
    }

    public ExactCollection(Object... elements) {
        this.size = elements.length;
        this.elements = elements;
    }

    @Override
    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        return (E) elements[index];
    }

    public void add(int index, E element) {
        this.elements[index] = element;
    }

    public int indexOf(Object element) {
        for (int i = 0; i < elements.length; i++) {
            if (element.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(elements, size);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        return (T[]) Arrays.copyOf(elements, size, a.getClass());
    }

    @Override
    public boolean remove(Object o) {
        int idx = indexOf(o);
        if (idx >= 0) {
            elements[idx] = null;
        }
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Collection)) {
            return false;
        }
        Collection<E> other = (Collection<E>) obj;
        if (size != other.size()) {
            return false;
        }

        return containsAll((Collection<?>) obj);
    }

    @Override
    public void clear() {
        Arrays.fill(elements, null);
    }

    private class Itr implements Iterator<E> {
        int cursor = 0;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @SuppressWarnings("unchecked")
        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException("Cursor: " + cursor + ", Size: " + size);
            }
            E element = (E) elements[cursor];
            cursor++;
            return element;
        }
    }
}
