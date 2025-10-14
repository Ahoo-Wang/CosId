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

package me.ahoo.cosid;


import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nonnull;

/**
 * String type ID generator.
 * 
 * <p>This interface defines the contract for generating distributed IDs in string format.
 * It is typically used when string IDs are preferred over numeric IDs, such as for
 * database primary keys that need to be URL-safe or human-readable.
 * 
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface StringIdGenerator {
    /**
     * Generate a distributed ID as a string value.
     * 
     * <p>This method generates a unique string identifier that is guaranteed to be
     * unique within the distributed system. The format and structure of the string
     * ID depends on the specific implementation.
     *
     * @return A unique distributed ID as a string value
     */
    @Nonnull
    String generateAsString();
}
