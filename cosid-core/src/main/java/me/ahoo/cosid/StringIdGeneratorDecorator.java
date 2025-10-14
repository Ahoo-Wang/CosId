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

import jakarta.annotation.Nonnull;

/**
 * String ID generator decorator for customizing string ID generation.
 * 
 * <p>This decorator allows customization of how numeric IDs are converted to string format
 * by wrapping an existing {@link IdGenerator} with a custom {@link IdConverter}. This is
 * useful when you want to:
 * <ul>
 *   <li>Use a specific string format (e.g., radix-36 instead of radix-62)</li>
 *   <li>Add prefixes or suffixes to generated IDs</li>
 *   <li>Apply custom encoding or transformation to IDs</li>
 * </ul>
 * 
 * <p>The decorator follows the standard decorator pattern, delegating ID generation to
 * the wrapped generator while overriding the ID conversion behavior.
 *
 * @author ahoo wang
 */
public class StringIdGeneratorDecorator implements IdGeneratorDecorator {

    /**
     * The actual ID generator being decorated.
     * 
     * <p>This is the underlying generator that produces the numeric IDs which will
     * then be converted to string format using the custom converter.
     */
    protected final IdGenerator actual;
    
    /**
     * The custom ID converter for transforming numeric IDs to string format.
     * 
     * <p>This converter will be used instead of the default converter provided by
     * the wrapped generator, allowing customization of the string representation.
     */
    protected final IdConverter idConverter;

    /**
     * Create a new StringIdGeneratorDecorator with the specified generator and converter.
     * 
     * <p>This constructor creates a decorator that will delegate ID generation to the
     * provided generator but use the specified converter for string conversion.
     *
     * @param actual The actual ID generator to delegate to
     * @param idConverter The custom ID converter for string conversion
     */
    public StringIdGeneratorDecorator(IdGenerator actual, IdConverter idConverter) {
        this.actual = actual;
        this.idConverter = idConverter;
    }

    /**
     * Get the custom ID converter used by this decorator.
     * 
     * <p>This method returns the converter that will be used to transform numeric IDs
     * to string format, overriding the default converter of the wrapped generator.
     *
     * @return The custom ID converter
     */
    @Nonnull
    @Override
    public IdConverter idConverter() {
        return idConverter;
    }
    
    /**
     * Get the actual (wrapped) ID generator that this decorator is enhancing.
     * 
     * <p>This method returns the underlying ID generator that this decorator is wrapping.
     * All ID generation requests are delegated to this actual generator.
     *
     * @return The actual ID generator being decorated
     */
    @Nonnull
    @Override
    public IdGenerator getActual() {
        return actual;
    }

}
