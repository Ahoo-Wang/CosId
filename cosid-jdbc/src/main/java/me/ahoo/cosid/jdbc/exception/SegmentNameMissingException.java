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

package me.ahoo.cosid.jdbc.exception;

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Strings;

/**
 * Segment Name Missing Exception.
 *
 * @author ahoo wang
 */
public class SegmentNameMissingException extends CosIdException {

    private final String name;

    /**
     * Constructs a new runtime exception with {@code null} as its
     * detail message.  The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause}.
     *
     * @param name name of segment
     */
    public SegmentNameMissingException(String name) {
        super(Strings.lenientFormat("name:[%s] missing.", name));
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
