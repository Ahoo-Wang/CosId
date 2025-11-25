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

package me.ahoo.cosid.machine;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
@Data
public class GuardianState {
    public static final GuardianState INITIAL = new GuardianState(0, null);
    private final long guardAt;
    @Nullable
    private final Throwable error;

    boolean isFailed() {
        return error != null;
    }

    static GuardianState success(final long guardAt) {
        return new GuardianState(guardAt, null);
    }

    static GuardianState failed(final long guardAt, final Throwable error) {
        return new GuardianState(guardAt, error);
    }
}
