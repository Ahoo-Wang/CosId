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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Integer Id Generator.
 *
 * @author ahoo wang
 */
@ThreadSafe
public class IntegerIdGenerator implements StringIdGenerator {
    
    protected final IdGenerator actual;
    
    public IntegerIdGenerator(IdGenerator actual) {
        this.actual = actual;
    }
    
    /**
     * Generate distributed ID of type int.
     *
     * @return generated distributed ID of type int
     * @throws IdOverflowException This exception is thrown when the ID overflows
     */
    public int generate() throws IdOverflowException {
        long id = actual.generate();
        ensureInteger(id);
        return (int) id;
    }
    
    /**
     * Generate distributed ID of type string.
     *
     * @return generated distributed ID of type string
     * @throws IdOverflowException This exception is thrown when the ID overflows
     */
    @Nonnull
    @Override
    public String generateAsString() throws IdOverflowException {
        long id = actual.generate();
        ensureInteger(id);
        return actual.idConverter().asString(id);
    }
    
    private void ensureInteger(long id) throws IdOverflowException {
        if (id < Integer.MIN_VALUE || id > Integer.MAX_VALUE) {
            throw new IdOverflowException(id);
        }
    }
    
    /**
     * ID Overflow Exception.
     */
    public static class IdOverflowException extends CosIdException {
        private final long id;
        
        public IdOverflowException(long id) {
            super("id [" + id + "] overflow.");
            this.id = id;
        }
        
        public long getId() {
            return id;
        }
    }
}
