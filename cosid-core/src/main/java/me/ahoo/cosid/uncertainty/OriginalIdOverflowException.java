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

package me.ahoo.cosid.uncertainty;

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Strings;

public class OriginalIdOverflowException extends CosIdException {
    private final long originalId;
    private final int originalIdBits;
    private final long maxOriginalId;
    
    public OriginalIdOverflowException(long originalId, int originalIdBits, long maxOriginalId) {
        super(Strings.lenientFormat("OriginalId[%s] overflow - originalIdBits[%s] - maxOriginalId[%s].", originalId, originalIdBits, maxOriginalId));
        this.originalId = originalId;
        this.originalIdBits = originalIdBits;
        this.maxOriginalId = maxOriginalId;
    }
    
    public long originalId() {
        return originalId;
    }
    
    public int originalIdBits() {
        return originalIdBits;
    }
    
    public long maxOriginalId() {
        return maxOriginalId;
    }
}
