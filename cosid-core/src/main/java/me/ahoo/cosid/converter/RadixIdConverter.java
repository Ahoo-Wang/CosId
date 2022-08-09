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

import me.ahoo.cosid.IdConverter;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public abstract class RadixIdConverter implements IdConverter {
    /**
     * 48.
     */
    private static final char ZERO = '0';
    /**
     * 57.
     */
    private static final char NINE = '9';
    /**
     * 65.
     */
    private static final char UPPERCASE_A = 'A';
    private static final int UPPERCASE_OFFSET = 10;
    /**
     * 90.
     */
    private static final char UPPERCASE_Z = 'Z';
    /**
     * 97.
     */
    private static final char LOWERCASE_A = 'a';
    private static final int LOWERCASE_OFFSET = 36;
    /**
     * 122.
     */
    private static final char LOWERCASE_Z = 'z';
    
    private static final char[] digits = {
        /*
         * offset: 0.
         * [48-57]
        */
        ZERO, '1', '2', '3', '4', '5', '6', '7', '8', NINE,
        /*
         * offset: 10.
         * [64-90]
        */
        UPPERCASE_A, 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', UPPERCASE_Z,
        /*
         * offset: 36.
         * [97-122]
        */
        LOWERCASE_A, 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', LOWERCASE_Z,
    };
    
    private static final char PAD_CHAR = ZERO;
    
    private final boolean padStart;
    private final int charSize;
    private final long maxId;
    
    protected RadixIdConverter(boolean padStart, int charSize) {
        Preconditions.checkArgument(charSize > 0 && charSize <= getMaxCharSize(), "charSize cannot be greater than MAX_CHAR_SIZE[%s]!", getMaxCharSize());
        this.padStart = padStart;
        this.charSize = charSize;
        if (getMaxCharSize() == charSize) {
            this.maxId = Long.MAX_VALUE;
        } else {
            this.maxId = Double.valueOf(Math.pow(getRadix(), charSize)).longValue();
        }
    }
    
    static int offset(char digitChar) {
        if (digitChar >= ZERO && digitChar <= NINE) {
            return digitChar - ZERO;
        }
        if (digitChar >= UPPERCASE_A && digitChar <= UPPERCASE_Z) {
            return digitChar - UPPERCASE_A + UPPERCASE_OFFSET;
        }
        if (digitChar >= LOWERCASE_A && digitChar <= LOWERCASE_Z) {
            return digitChar - LOWERCASE_A + LOWERCASE_OFFSET;
        }
        return -1;
    }
    
    boolean isPadStart() {
        return padStart;
    }
    
    public int getCharSize() {
        return charSize;
    }
    
    abstract int getRadix();
    
    abstract int getMaxCharSize();
    
    @Override
    public String asString(long id) {
        
        Preconditions.checkArgument(id > -1, "id[%s] must be greater than -1!", id);
        
        final int maxCharSize = getMaxCharSize();
        
        if (charSize < maxCharSize) {
            Preconditions.checkArgument(id < maxId, "id[%s] cannot be greater than maxId:[%s]!", id, maxId);
        }
        
        char[] buf = new char[charSize];
        int charIdx = charSize;
        final int radix = getRadix();
        while (id > 0) {
            int mod = (int) (id % radix);
            buf[--charIdx] = digits[mod];
            id = id / radix;
        }
        
        if (padStart && charIdx > 0) {
            while (charIdx > 0) {
                buf[--charIdx] = PAD_CHAR;
            }
        }
        
        return new String(buf, charIdx, (charSize - charIdx));
    }
    
    public long asLong(String idString) {
        char firstChar = idString.charAt(0);
        if (firstChar < ZERO) {
            throw new NumberFormatException(Strings.lenientFormat("For input string: [%s]!", idString));
        }
        long result = 0;
        int charIdx = 0;
        int charLen = idString.length();
        if (charLen > charSize) {
            throw new NumberFormatException(Strings.lenientFormat("For input string:[%s]. charLen cannot be greater than charSize:[%s]!", idString, charSize));
        }
        final int radix = getRadix();
        while (charIdx < charLen) {
            char digitChar = idString.charAt(charIdx++);
            int digit = offset(digitChar);
            if (digit < 0) {
                throw new NumberFormatException(Strings.lenientFormat("For input string:[%s]. digitChar:[%s]@[%s] !", idString, digitChar, charIdx));
            }
            result *= radix;
            result += digit;
        }
        return result;
    }
}
