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

package me.ahoo.cosid.codec;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.internal.PatternPeriod;
import me.ahoo.cosid.segment.Period;

import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * {@link GroupedCodec#datePrefix(Period, String, IdCodec, UnaryOperator)}: the period key and delimiter sit between the
 * value's text and the outer decorations.
 *
 * <p>Encoding composes the decorations around a prefix of the period key once per key and keeps the last composition, so
 * a generator encodes without composing until its period changes. Decoding composes them around a layer that splits the
 * key off and remembers it.
 */
final class PeriodWithin implements GroupedCodec {
    private final Period period;
    private final String delimiter;
    private final IdCodec inner;
    private final UnaryOperator<IdCodec> outer;
    /** The length of the period keys; -1 when unknown, the key then ends at the first delimiter. */
    private final int keyLength;
    private volatile Encoder last;

    PeriodWithin(Period period, String delimiter, IdCodec inner, UnaryOperator<IdCodec> outer) {
        this.period = Objects.requireNonNull(period, "period");
        this.delimiter = Objects.requireNonNull(delimiter, "delimiter");
        this.inner = Objects.requireNonNull(inner, "inner");
        this.outer = Objects.requireNonNull(outer, "outer");
        this.keyLength = period instanceof PatternPeriod preset ? preset.pattern().length() : -1;
    }

    @Override
    public String encode(GroupedId id) {
        Encoder encoder = last;
        if (encoder == null || !encoder.key().equals(id.period())) {
            encoder = new Encoder(id.period(), compose(inner.withPrefix(id.period() + delimiter)));
            last = encoder;
        }
        return encoder.codec().encode(id.value());
    }

    @Override
    public GroupedId decode(String text) {
        KeyLayer layer = new KeyLayer();
        long value = compose(layer).decode(text);
        if (layer.key == null) {
            throw new IllegalArgumentException("[" + text + "] has no period");
        }
        // Refuses a key that is not one of the period's.
        period.endOf(layer.key, ZoneOffset.UTC);
        return new GroupedId(layer.key, value);
    }

    private IdCodec compose(IdCodec periodLayer) {
        return Objects.requireNonNull(outer.apply(periodLayer), "outer returned null");
    }

    private record Encoder(String key, IdCodec codec) {
    }

    /** Splits the period key off the text it is given and decodes the rest as the value; decoding only. */
    private final class KeyLayer implements IdCodec {
        private String key;

        @Override
        public String encode(long id) {
            throw new UnsupportedOperationException("decoding only");
        }

        @Override
        public long decode(String text) {
            int at;
            if (keyLength > 0) {
                if (text.length() < keyLength + delimiter.length() || !text.startsWith(delimiter, keyLength)) {
                    throw new IllegalArgumentException("[" + text + "] has no " + keyLength + "-character period followed by [" + delimiter + "]");
                }
                at = keyLength;
            } else if (delimiter.isEmpty()) {
                throw new UnsupportedOperationException("A period key without a delimiter cannot be decoded: the key has no known length");
            } else {
                at = text.indexOf(delimiter);
                if (at <= 0) {
                    throw new IllegalArgumentException("[" + text + "] has no period followed by [" + delimiter + "]");
                }
            }
            key = text.substring(0, at);
            return inner.decode(text.substring(at + delimiter.length()));
        }
    }
}
