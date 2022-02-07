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

import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;

/**
 * IdConverter Definition.
 *
 * @author ahoo wang
 */
public class IdConverterDefinition {
    
    private Type type = Type.TO_STRING;
    private String prefix = PrefixIdConverter.EMPTY_PREFIX;
    private Radix radix = new Radix();
    
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
    
    public Radix getRadix() {
        return radix;
    }
    
    public void setRadix(Radix radix) {
        this.radix = radix;
    }
    
    /**
     * Radix62IdConverter Config
     */
    public static class Radix {
        
        private boolean padStart;
        private int charSize = Radix62IdConverter.MAX_CHAR_SIZE;
        
        public boolean isPadStart() {
            return padStart;
        }
        
        public void setPadStart(boolean padStart) {
            this.padStart = padStart;
        }
        
        public int getCharSize() {
            return charSize;
        }
        
        public void setCharSize(int charSize) {
            this.charSize = charSize;
        }
    }
    
    /**
     * IdConverter Type
     */
    public enum Type {
        TO_STRING,
        SNOWFLAKE_FRIENDLY,
        RADIX
    }
}
