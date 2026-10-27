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

package me.ahoo.cosid.migration.source;

/**
 * Reads the 4.x data of a namespace (09 §4). Read-only, except for {@link #seal}, the one write the migration makes to
 * a 4.x store (09 §2).
 */
public interface SourceReader extends AutoCloseable {
    /**
     * Reads every segment and machine record of a namespace.
     *
     * @param namespace the 4.x namespace
     * @return the snapshot
     */
    SourceSnapshot read(String namespace);

    /**
     * Seals a segment: adds {@code increment} to its value in one atomic step, as a 4.x fetch of that size would. A 4.x
     * instance still running afterwards only gets values above the result.
     *
     * @param namespace the 4.x namespace
     * @param name      the 4.x segment name
     * @param increment the seal gap, positive
     * @return the value after the seal; everything 4.x reserved before it is at most {@code result - increment}
     * @throws IllegalStateException if the segment does not exist or would overflow
     */
    long seal(String namespace, String name, long increment);

    @Override
    void close();

    /**
     * {@code value + increment}, or an error naming the segment when that passes {@code Long.MAX_VALUE}.
     */
    static long sealed(String name, long value, long increment) {
        if (value > Long.MAX_VALUE - increment) {
            throw new IllegalStateException("Sealing segment [" + name + "] at " + value + " + " + increment + " overflows; use a smaller --seal-gap");
        }
        return value + increment;
    }
}
