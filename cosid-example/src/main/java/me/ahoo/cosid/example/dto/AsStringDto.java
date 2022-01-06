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

package me.ahoo.cosid.example.dto;

import me.ahoo.cosid.jackson.AsString;

/**
 * @author ahoo wang
 */
public class AsStringDto {

    @AsString
    private Long id;
    @AsString(AsString.Type.RADIX)
    private Long radixId;

    @AsString(value = AsString.Type.RADIX, radixPadStart = true)
    private Long radixPadStartId;

    @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 10)
    private Long radixPadStartCharSize10Id;

    @AsString(AsString.Type.FRIENDLY_ID)
    private long friendlyId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRadixId() {
        return radixId;
    }

    public void setRadixId(Long radixId) {
        this.radixId = radixId;
    }

    public Long getRadixPadStartId() {
        return radixPadStartId;
    }

    public void setRadixPadStartId(Long radixPadStartId) {
        this.radixPadStartId = radixPadStartId;
    }

    public Long getRadixPadStartCharSize10Id() {
        return radixPadStartCharSize10Id;
    }

    public void setRadixPadStartCharSize10Id(Long radixPadStartCharSize10Id) {
        this.radixPadStartCharSize10Id = radixPadStartCharSize10Id;
    }

    public long getFriendlyId() {
        return friendlyId;
    }

    public void setFriendlyId(long friendlyId) {
        this.friendlyId = friendlyId;
    }
}
