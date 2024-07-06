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

package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.GroupedPrefixIdConverter;
import me.ahoo.cosid.converter.Radix36IdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;

/**
 * IdConverter Definition.
 *
 * @author ahoo wang
 */
public class IdConverterDefinition {

    private Type type = Type.RADIX;
    private String prefix;
    private GroupPrefix groupPrefix = new GroupPrefix();
    private String suffix;
    private Radix radix = new Radix();
    private Radix36 radix36 = new Radix36();
    private ToString toString;
    private Custom custom;
    private DatePrefix datePrefix = new DatePrefix();

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public GroupPrefix getGroupPrefix() {
        return groupPrefix;
    }

    public void setGroupPrefix(GroupPrefix groupPrefix) {
        this.groupPrefix = groupPrefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public Radix getRadix() {
        return radix;
    }

    public void setRadix(Radix radix) {
        this.radix = radix;
    }

    public Radix36 getRadix36() {
        return radix36;
    }

    public void setRadix36(Radix36 radix36) {
        this.radix36 = radix36;
    }

    public ToString getToString() {
        return toString;
    }

    public void setToString(ToString toString) {
        this.toString = toString;
    }

    public Custom getCustom() {
        return custom;
    }

    public void setCustom(Custom custom) {
        this.custom = custom;
    }

    public DatePrefix getDatePrefix() {
        return datePrefix;
    }

    public IdConverterDefinition setDatePrefix(DatePrefix datePrefix) {
        this.datePrefix = datePrefix;
        return this;
    }

    /**
     * Radix62IdConverter Config.
     */
    public static class Radix implements PadStartIdConverter {

        private boolean padStart = true;
        private int charSize = Radix62IdConverter.MAX_CHAR_SIZE;

        @Override
        public boolean isPadStart() {
            return padStart;
        }

        public void setPadStart(boolean padStart) {
            this.padStart = padStart;
        }

        @Override
        public int getCharSize() {
            return charSize;
        }

        public void setCharSize(int charSize) {
            this.charSize = charSize;
        }
    }

    public static class Radix36 implements PadStartIdConverter {

        private boolean padStart = true;
        private int charSize = Radix36IdConverter.MAX_CHAR_SIZE;

        @Override
        public boolean isPadStart() {
            return padStart;
        }

        public void setPadStart(boolean padStart) {
            this.padStart = padStart;
        }

        @Override
        public int getCharSize() {
            return charSize;
        }

        public void setCharSize(int charSize) {
            this.charSize = charSize;
        }
    }

    public static class ToString implements PadStartIdConverter {

        private boolean padStart = false;
        private int charSize = Radix62IdConverter.MAX_CHAR_SIZE;

        @Override
        public boolean isPadStart() {
            return padStart;
        }

        public void setPadStart(boolean padStart) {
            this.padStart = padStart;
        }

        @Override
        public int getCharSize() {
            return charSize;
        }

        public void setCharSize(int charSize) {
            this.charSize = charSize;
        }
    }

    public static class GroupPrefix {
        private boolean enabled = false;
        private String delimiter = GroupedPrefixIdConverter.DEFAULT_DELIMITER;
        private boolean beforePrefix = true;

        public boolean isEnabled() {
            return enabled;
        }

        public GroupPrefix setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public String getDelimiter() {
            return delimiter;
        }

        public GroupPrefix setDelimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public boolean isBeforePrefix() {
            return beforePrefix;
        }

        public GroupPrefix setBeforePrefix(boolean beforePrefix) {
            this.beforePrefix = beforePrefix;
            return this;
        }
    }

    public static class DatePrefix {
        private boolean enabled = false;
        private String delimiter = GroupedPrefixIdConverter.DEFAULT_DELIMITER;
        private String pattern = "yyMMdd";
        private boolean beforePrefix = true;

        public boolean isEnabled() {
            return enabled;
        }

        public DatePrefix setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public String getDelimiter() {
            return delimiter;
        }

        public DatePrefix setDelimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public String getPattern() {
            return pattern;
        }

        public DatePrefix setPattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public boolean isBeforePrefix() {
            return beforePrefix;
        }

        public DatePrefix setBeforePrefix(boolean beforePrefix) {
            this.beforePrefix = beforePrefix;
            return this;
        }
    }

    public static class Custom {
        private Class<? extends IdConverter> type;

        public Class<? extends IdConverter> getType() {
            return type;
        }

        public Custom setType(Class<? extends IdConverter> type) {
            this.type = type;
            return this;
        }
    }

    /**
     * IdConverter Type.
     */
    public enum Type {
        TO_STRING,
        SNOWFLAKE_FRIENDLY,
        RADIX,
        RADIX36,
        CUSTOM
    }

    interface PadStartIdConverter {
        boolean isPadStart();

        int getCharSize();
    }
}
