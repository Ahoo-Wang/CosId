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

/**
 * An id does not fit where it goes (03-core-model.md §5): the narrower type it is converted to, or the bits left
 * beside the random bits of {@link IdGenerators#uncertain}.
 */
public class IdOverflowException extends CosIdException {
    private final long id;

    /**
     * The id overflows {@code int}.
     *
     * @param id the id
     */
    public IdOverflowException(long id) {
        this(id, "Id " + id + " overflows int");
    }

    /**
     * The id overflows, as {@code message} tells.
     *
     * @param id      the id
     * @param message the message
     */
    public IdOverflowException(long id, String message) {
        super(message);
        this.id = id;
    }

    /**
     * The id that overflowed.
     *
     * @return the id
     */
    public long id() {
        return id;
    }
}
