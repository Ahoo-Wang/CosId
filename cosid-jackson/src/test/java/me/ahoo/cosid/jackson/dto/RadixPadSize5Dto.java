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

package me.ahoo.cosid.jackson.dto;

import me.ahoo.cosid.jackson.AsString;

/**
 * RadixPadSize5Dto .
 *
 * @author ahoo wang
 */
public class RadixPadSize5Dto {
    @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 5)
    private long primitiveLong;
    @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 5)
    private Long objectLong;
    
    public long getPrimitiveLong() {
        return primitiveLong;
    }
    
    public void setPrimitiveLong(long primitiveLong) {
        this.primitiveLong = primitiveLong;
    }
    
    public Long getObjectLong() {
        return objectLong;
    }
    
    public void setObjectLong(Long objectLong) {
        this.objectLong = objectLong;
    }
}
