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

package me.ahoo.cosid.converter;

/**
 * Radix-36 string ID converter using characters 0-9 and A-Z.
 *
 * <p>Encodes long IDs as strings using 36 characters (10 digits + 26 uppercase letters).
 * Maximum 13 characters needed for full long range.
 *
 * @author ahoo wang
 */
public final class Radix36IdConverter extends RadixIdConverter {

    /**
     * Maximum character size (13 for full long range).
     */
    public static final int MAX_CHAR_SIZE = 13;
    /**
     * Radix value (36).
     */
    public static final int RADIX = 36;

    /**
     * Shared instance without padding.
     */
    public static final Radix36IdConverter INSTANCE = new Radix36IdConverter(false, MAX_CHAR_SIZE);
    /**
     * Shared instance with padding.
     */
    public static final Radix36IdConverter PAD_START = new Radix36IdConverter(true, MAX_CHAR_SIZE);

    /**
     * Gets an instance with specified parameters.
     *
     * @param padStart whether to pad
     * @param charSize character size
     * @return converter instance
     */
    public static Radix36IdConverter of(boolean padStart, int charSize) {

        if (INSTANCE.isPadStart() == padStart && INSTANCE.getCharSize() == charSize) {
            return INSTANCE;
        }

        if (PAD_START.isPadStart() == padStart && PAD_START.getCharSize() == charSize) {
            return PAD_START;
        }

        return new Radix36IdConverter(padStart, charSize);
    }

    /**
     * Creates a new converter.
     *
     * @param padStart whether to pad with leading zeros
     * @param charSize the character size
     */
    public Radix36IdConverter(boolean padStart, int charSize) {
        super(padStart, charSize);
    }

    @Override
    int getRadix() {
        return RADIX;
    }

    @Override
    int getMaxCharSize() {
        return MAX_CHAR_SIZE;
    }
}
