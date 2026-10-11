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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class MongoReactiveDeprecationTest {
    
    @Test
    void warnsOnceWhenOnlyTheReactiveDriverIsPresent() {
        AtomicBoolean warned = new AtomicBoolean(false);
        
        Assertions.assertTrue(MongoReactiveDeprecation.warnIfSelected(false, warned));
        Assertions.assertFalse(MongoReactiveDeprecation.warnIfSelected(false, warned));
    }
    
    @Test
    void staysSilentWithTheSynchronousDriver() {
        Assertions.assertFalse(MongoReactiveDeprecation.warnIfSelected(true, new AtomicBoolean(false)));
        Assertions.assertDoesNotThrow(() -> MongoReactiveDeprecation.warnIfSelected());
    }
}
