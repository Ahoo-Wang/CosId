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


package me.ahoo.cosid.integrations.jackson;

import me.ahoo.cosid.codec.Friendly;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.time.Layout;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Function;

/** The codec an {@link AsString} names. */
final class AsStringCodecs {
    /** Decimal text, the {@code long} itself. */
    static final IdCodec TO_STRING = new IdCodec() {
        @Override
        public String encode(long id) {
            return Long.toString(id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text);
        }
    };

    private AsStringCodecs() {
    }

    /**
     * The codec of an annotation.
     *
     * @param asString   the annotation
     * @param generators the codec of a generator name, empty without a bound runtime
     * @return the codec
     * @throws IllegalArgumentException if the annotation is inconsistent, or names a generator without a runtime
     */
    static IdCodec of(AsString asString, Optional<Function<String, IdCodec>> generators) {
        if (!asString.generator().isEmpty()) {
            if (!isInlineDefault(asString)) {
                throw new IllegalArgumentException("@AsString(generator = \"" + asString.generator()
                    + "\") takes the generator's codec: leave the inline attributes unset");
            }
            return generators.orElseThrow(() -> new IllegalArgumentException("@AsString(generator = \"" + asString.generator()
                    + "\") needs a CosIdJacksonModule bound to a CosIdRuntime registered with the JsonMapper"))
                .apply(asString.generator());
        }
        return switch (asString.codec()) {
            case TO_STRING -> TO_STRING;
            case RADIX -> Radix.of(asString.radix(), asString.width() == 0 ? Radix.maxWidth(asString.radix()) : asString.width(),
                asString.padStart());
            case FRIENDLY -> Friendly.of(layout(asString), asString.zone().isEmpty() ? ZoneId.systemDefault() : ZoneId.of(asString.zone()),
                asString.padStart());
        };
    }

    private static Layout layout(AsString asString) {
        Layout preset = asString.layout().layout();
        boolean bits = asString.timeBits() != 0 || asString.nodeBits() != -1 || asString.seqBits() != 0;
        if (bits && (asString.timeBits() == 0 || asString.nodeBits() == -1 || asString.seqBits() == 0)) {
            throw new IllegalArgumentException("@AsString: set timeBits, nodeBits and seqBits together");
        }
        if (!bits && asString.epoch() == -1) {
            return preset;
        }
        Instant epoch = asString.epoch() == -1 ? preset.epoch() : Instant.ofEpochMilli(asString.epoch());
        int seqBits = bits ? asString.seqBits() : preset.seqBits();
        return new Layout(epoch, preset.unit(), bits ? asString.timeBits() : preset.timeBits(), bits ? asString.nodeBits() : preset.nodeBits(),
            seqBits, Layout.defaultSeqResetThreshold(seqBits));
    }

    private static boolean isInlineDefault(AsString asString) {
        return asString.codec() == AsString.Codec.RADIX && asString.radix() == 62 && asString.width() == 0 && asString.padStart()
            && asString.layout() == AsString.Preset.SNOWFLAKE_MS && asString.timeBits() == 0 && asString.nodeBits() == -1
            && asString.seqBits() == 0 && asString.epoch() == -1 && asString.zone().isEmpty();
    }
}
