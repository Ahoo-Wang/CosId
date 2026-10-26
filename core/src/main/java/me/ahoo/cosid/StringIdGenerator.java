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

import me.ahoo.cosid.codec.IdCodec;

import java.util.Objects;

/**
 * An {@link IdGenerator} paired with the {@link IdCodec} that turns its ids into text.
 *
 * <p>Generation and encoding stay separate (03-core-model.md §1): wrapping {@link #ids()} never changes the encoding.
 *
 * @param ids   the generator
 * @param codec the codec
 */
public record StringIdGenerator(IdGenerator ids, IdCodec codec) {
    public StringIdGenerator {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(codec, "codec");
    }

    public String generate() {
        return codec.encode(ids.generate());
    }
}
