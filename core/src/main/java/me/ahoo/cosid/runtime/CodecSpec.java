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

import me.ahoo.cosid.codec.IdCodec;

import java.time.ZoneId;
import java.util.Objects;

/**
 * An item of a generator's codec list (07-runtime-and-spring.md §4.1). The first item turns the id into text
 * ({@link Radix}, {@link Friendly}, {@link Custom}, {@link Instance}); the following ones decorate the text
 * ({@link Prefix}, {@link Suffix}, {@link DatePrefix}).
 *
 * <p>The variants are the current snapshot of a closed set of specs: a minor version may add a variant, so keep a
 * {@code default} branch in a {@code switch} over them (D40).
 */
public sealed interface CodecSpec {
    /**
     * {@code me.ahoo.cosid.codec.Radix}.
     *
     * @param radix    the base
     * @param width    the most digits
     * @param padStart whether to pad with leading zeros
     */
    record Radix(int radix, int width, boolean padStart) implements CodecSpec {
    }

    /**
     * {@code me.ahoo.cosid.codec.Friendly}; time-based generators only.
     *
     * @param zone     the time zone
     * @param padStart whether to pad the node and sequence
     */
    record Friendly(ZoneId zone, boolean padStart) implements CodecSpec {
        public Friendly {
            Objects.requireNonNull(zone, "zone");
        }
    }

    /**
     * A text prefix.
     *
     * @param prefix the prefix
     */
    record Prefix(String prefix) implements CodecSpec {
        public Prefix {
            Objects.requireNonNull(prefix, "prefix");
        }
    }

    /**
     * A text suffix.
     *
     * @param suffix the suffix
     */
    record Suffix(String suffix) implements CodecSpec {
        public Suffix {
            Objects.requireNonNull(suffix, "suffix");
        }
    }

    /**
     * {@code me.ahoo.cosid.codec.DatePrefix}.
     *
     * @param pattern   the date pattern
     * @param delimiter the text between the date and the id
     * @param source    where the date comes from
     * @param zone      the time zone of the date
     */
    record DatePrefix(String pattern, String delimiter, me.ahoo.cosid.codec.DatePrefix.Source source,
                      ZoneId zone) implements CodecSpec {
        public DatePrefix {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(delimiter, "delimiter");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(zone, "zone");
        }
    }

    /**
     * A user codec, by class name or (in Spring) bean name, resolved through {@link RuntimeOptions#codecs()}.
     *
     * @param reference the class or bean name
     */
    record Custom(String reference) implements CodecSpec {
        public Custom {
            Objects.requireNonNull(reference, "reference");
        }
    }

    /**
     * A codec instance, for specs built in code.
     *
     * @param codec the codec
     */
    record Instance(IdCodec codec) implements CodecSpec {
        public Instance {
            Objects.requireNonNull(codec, "codec");
        }
    }
}
