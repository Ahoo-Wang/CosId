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

package me.ahoo.cosid.accessor;

import me.ahoo.cosid.IdGenerator;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

/**
 * CosId accessor for automatic ID injection into objects.
 *
 * <p>This interface combines multiple capabilities for working with ID fields in objects:
 * <ul>
 *   <li>{@link CosIdGetter} - Getting ID values from objects</li>
 *   <li>{@link CosIdSetter} - Setting ID values on objects</li>
 *   <li>{@link IdMetadata} - Providing metadata about ID fields</li>
 *   <li>{@link EnsureId} - Ensuring objects have IDs assigned</li>
 * </ul>
 *
 * <p>The accessor is used by the CosId framework to automatically find and populate
 * ID fields in objects when they are persisted or processed. This enables a
 * "convention over configuration" approach where ID fields are automatically
 * detected and populated without explicit code.
 *
 * <p>The interface includes utility methods for checking ID type compatibility
 * and ensuring reflective access to fields.
 *
 * @author ahoo wang
 */
public interface CosIdAccessor extends CosIdGetter, CosIdSetter, IdMetadata, EnsureId {

    /**
     * A sentinel value representing a "not found" accessor.
     *
     * <p>This is used when no valid ID field can be found in an object, allowing
     * the framework to handle missing ID fields gracefully without null checks.
     */
    NotFound NOT_FOUND = new NotFound();

    /**
     * Check if the specified ID type is supported by CosId.
     *
     * <p>This method verifies that the provided ID type is one of the supported
     * types for automatic ID injection:
     * <ul>
     *   <li>{@link String}</li>
     *   <li>{@link Long} (boxed)</li>
     *   <li>{@code long} (primitive)</li>
     *   <li>{@link Integer} (boxed)</li>
     *   <li>{@code int} (primitive)</li>
     * </ul>
     *
     * @param idType The ID type to check
     * @return {@code true} if the type is supported, {@code false} otherwise
     */
    static boolean availableType(Class<?> idType) {
        return String.class.equals(idType)
            || Long.class.equals(idType)
            || long.class.equals(idType)
            || Integer.class.equals(idType)
            || int.class.equals(idType)
            ;
    }

    /**
     * Ensure that the specified accessible object (field/method) is accessible.
     *
     * <p>This utility method makes the provided accessible object accessible if
     * it is not already, allowing the framework to access private or protected
     * fields and methods for ID injection.
     *
     * @param accessibleObject The object to make accessible
     */
    static void ensureAccessible(AccessibleObject accessibleObject) {
        if (!accessibleObject.isAccessible()) {
            accessibleObject.setAccessible(true);
        }
    }

    /**
     * Sentinel implementation representing a "not found" accessor.
     *
     * <p>This implementation provides null/default behavior for all accessor
     * methods, allowing the framework to handle cases where no valid ID field
     * is found without explicit null checks throughout the code.
     */
    class NotFound implements CosIdAccessor {

        /**
         * Get the ID definition (always returns null for NotFound).
         *
         * @return null
         */
        @Override
        public IdDefinition getIdDefinition() {
            return null;
        }

        /**
         * Get the ID generator (always returns null for NotFound).
         *
         * @return null
         */
        @Override
        public IdGenerator getIdGenerator() {
            return null;
        }

        /**
         * Get the ID field (always returns null for NotFound).
         *
         * @return null
         */
        @Override
        public Field getIdField() {
            return null;
        }

        /**
         * Get the ID value from the target object (always returns null for NotFound).
         *
         * @param target The target object
         * @return null
         */
        @Override
        public Object getId(Object target) {
            return null;
        }

        /**
         * Set the ID value on the target object (no-op for NotFound).
         *
         * @param target The target object
         * @param id     The ID value to set
         */
        @Override
        public void setId(Object target, Object id) {

        }

        /**
         * Ensure the target object has an ID (always returns false for NotFound).
         *
         * @param target The target object
         * @return false
         */
        @Override
        public boolean ensureId(Object target) {
            return false;
        }
    }

}
