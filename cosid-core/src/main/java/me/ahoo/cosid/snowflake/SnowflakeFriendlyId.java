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

package me.ahoo.cosid.snowflake;

import jakarta.annotation.Nonnull;

/**
 * Snowflake FriendlyId.
 *
 * @author ahoo wang
 */
public interface SnowflakeFriendlyId extends SnowflakeId {
    
    @Nonnull
    SnowflakeIdStateParser getParser();
    
    @Nonnull
    default SnowflakeIdState friendlyId(long id) {
        return getParser().parse(id);
    }
    
    @Nonnull
    default SnowflakeIdState friendlyId() {
        long id = generate();
        return friendlyId(id);
    }
    
    @Nonnull
    default SnowflakeIdState ofFriendlyId(String friendlyId) {
        return getParser().parse(friendlyId);
    }
    
}
