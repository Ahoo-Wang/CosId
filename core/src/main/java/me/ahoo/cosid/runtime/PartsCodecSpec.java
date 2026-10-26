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

package me.ahoo.cosid.runtime;

import me.ahoo.cosid.codec.PartsCodec;

import java.time.ZoneId;
import java.util.Objects;

/**
 * The codec of a {@link GeneratorSpec.CosId80} generator.
 */
public sealed interface PartsCodecSpec {
    /**
     * {@link PartsCodec#radix}.
     *
     * @param radix the base, 62 or 36 as in 4.x
     */
    record Radix(int radix) implements PartsCodecSpec {
    }

    /**
     * {@link PartsCodec#friendly}.
     *
     * @param zone the time zone
     */
    record Friendly(ZoneId zone) implements PartsCodecSpec {
        public Friendly {
            Objects.requireNonNull(zone, "zone");
        }
    }

    /**
     * A codec instance, for specs built in code.
     *
     * @param codec the codec
     */
    record Instance(PartsCodec codec) implements PartsCodecSpec {
        public Instance {
            Objects.requireNonNull(codec, "codec");
        }
    }
}
