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

package me.ahoo.cosid.segment;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NextIdSegmentExpiredExceptionTest {

    @Test
    void exceptionShouldExposeCurrentAndRejectedNextSegment() {
        IdSegment current = new DefaultIdSegment(20, 10);
        IdSegment next = new DefaultIdSegment(10, 10);

        NextIdSegmentExpiredException error = new NextIdSegmentExpiredException(current, next);

        assertSame(current, error.getCurrent());
        assertSame(next, error.getNext());
        assertTrue(error.getMessage().contains("The next IdSegment:[" + next + "] cannot be before the current IdSegment:[" + current + "]"));
        assertTrue(error.getMessage().contains("times:["));
    }
}
