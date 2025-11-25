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
 * Configuration definition for ID converters in CosId.
 *
 * <p>This class defines how IDs should be converted between their internal representation
 * and external string formats. It supports various conversion types including radix conversion,
 * friendly formatting, and custom converters.</p>
 *
 * <p>Supported converter types:
 * <ul>
 *   <li>TO_STRING - Simple string conversion</li>
 *   <li>SNOWFLAKE_FRIENDLY - Human-readable snowflake ID format</li>
 *   <li>RADIX - Base62 radix conversion</li>
 *   <li>RADIX36 - Base36 radix conversion</li>
 *   <li>CUSTOM - Custom converter implementation</li>
 * </ul>
 *
 * <p>The configuration also supports prefixes, suffixes, and grouping options
 * to customize the final ID format.</p>
 *
 * @author ahoo wang
 */
public class IdConverterDefinition {

    /**
     * The type of ID converter to use.
     * Default is RADIX.
     */
    private Type type = Type.RADIX;

    /**
     * Static prefix to prepend to converted IDs.
     */
    private String prefix;

    /**
     * Configuration for grouped prefix functionality.
     */
    private GroupPrefix groupPrefix = new GroupPrefix();

    /**
     * Static suffix to append to converted IDs.
     */
    private String suffix;

    /**
     * Configuration for radix62 conversion.
     */
    private Radix radix = new Radix();

    /**
     * Configuration for radix36 conversion.
     */
    private Radix36 radix36 = new Radix36();

    /**
     * Configuration for toString conversion.
     */
    private ToString toString;

    /**
     * Configuration for custom converter.
     */
    private Custom custom;

    /**
     * Configuration for date prefix functionality.
     */
    private DatePrefix datePrefix = new DatePrefix();

    /**
     * Configuration for friendly format conversion.
     */
    private Friendly friendly = new Friendly();

    /**
     * Gets the converter type.
     *
     * @return the converter type
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the converter type.
     *
     * @param type the converter type to set
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Gets the static prefix.
     *
     * @return the prefix string, or null if not set
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Sets the static prefix.
     *
     * @param prefix the prefix to set
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Gets the group prefix configuration.
     *
     * @return the group prefix configuration
     */
    public GroupPrefix getGroupPrefix() {
        return groupPrefix;
    }

    /**
     * Sets the group prefix configuration.
     *
     * @param groupPrefix the group prefix configuration to set
     */
    public void setGroupPrefix(GroupPrefix groupPrefix) {
        this.groupPrefix = groupPrefix;
    }

    /**
     * Gets the static suffix.
     *
     * @return the suffix string, or null if not set
     */
    public String getSuffix() {
        return suffix;
    }

    /**
     * Sets the static suffix.
     *
     * @param suffix the suffix to set
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    /**
     * Gets the radix62 configuration.
     *
     * @return the radix configuration
     */
    public Radix getRadix() {
        return radix;
    }

    /**
     * Sets the radix62 configuration.
     *
     * @param radix the radix configuration to set
     */
    public void setRadix(Radix radix) {
        this.radix = radix;
    }

    /**
     * Gets the radix36 configuration.
     *
     * @return the radix36 configuration
     */
    public Radix36 getRadix36() {
        return radix36;
    }

    /**
     * Sets the radix36 configuration.
     *
     * @param radix36 the radix36 configuration to set
     */
    public void setRadix36(Radix36 radix36) {
        this.radix36 = radix36;
    }

    /**
     * Gets the toString configuration.
     *
     * @return the toString configuration, or null if not set
     */
    public ToString getToString() {
        return toString;
    }

    /**
     * Sets the toString configuration.
     *
     * @param toString the toString configuration to set
     */
    public void setToString(ToString toString) {
        this.toString = toString;
    }

    /**
     * Gets the custom converter configuration.
     *
     * @return the custom configuration, or null if not set
     */
    public Custom getCustom() {
        return custom;
    }

    /**
     * Sets the custom converter configuration.
     *
     * @param custom the custom configuration to set
     */
    public void setCustom(Custom custom) {
        this.custom = custom;
    }

    /**
     * Gets the date prefix configuration.
     *
     * @return the date prefix configuration
     */
    public DatePrefix getDatePrefix() {
        return datePrefix;
    }

    /**
     * Sets the date prefix configuration.
     *
     * @param datePrefix the date prefix configuration to set
     */
    public void setDatePrefix(DatePrefix datePrefix) {
        this.datePrefix = datePrefix;
    }

    /**
     * Gets the friendly format configuration.
     *
     * @return the friendly configuration
     */
    public Friendly getFriendly() {
        return friendly;
    }

    /**
     * Sets the friendly format configuration.
     *
     * @param friendly the friendly configuration to set
     */
    public void setFriendly(Friendly friendly) {
        this.friendly = friendly;
    }

    /**
     * Configuration for Radix62 ID converter.
     *
     * <p>Radix62 converter encodes IDs using base62 encoding (0-9, A-Z, a-z)
     * to produce compact alphanumeric strings.</p>
     */
    public static class Radix implements PadStartIdConverter {

        /**
         * Whether to pad the start of converted IDs with zeros.
         * Default is true.
         */
        private boolean padStart = true;

        /**
         * The character size for padding.
         * Default is {@link Radix62IdConverter#MAX_CHAR_SIZE}.
         */
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

    public static class Friendly implements PadStartCapable {

        private boolean padStart = false;

        @Override
        public boolean isPadStart() {
            return padStart;
        }

        public void setPadStart(boolean padStart) {
            this.padStart = padStart;
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
     * Enumeration of supported ID converter types.
     */
    public enum Type {
        /**
         * Simple string conversion using toString().
         */
        TO_STRING,

        /**
         * Friendly format for snowflake IDs with timestamp formatting.
         */
        SNOWFLAKE_FRIENDLY,

        /**
         * Base62 radix conversion for compact alphanumeric IDs.
         */
        RADIX,

        /**
         * Base36 radix conversion for uppercase alphanumeric IDs.
         */
        RADIX36,

        /**
         * Custom converter implementation specified by class.
         */
        CUSTOM
    }

    interface PadStartCapable {
        boolean isPadStart();
    }

    interface PadStartIdConverter extends PadStartCapable {

        int getCharSize();
    }
}
