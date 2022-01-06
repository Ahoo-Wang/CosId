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

package me.ahoo.cosid.jvm;

import me.ahoo.cosid.IdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 * Creation time: 2019/11/21 20:51
 */
public class AtomicLongGenerator implements IdGenerator {
    public final static IdGenerator INSTANCE = new AtomicLongGenerator();
    private final AtomicLong idGen = new AtomicLong();

    @Override
    public long generate() {
        return idGen.incrementAndGet();
    }
}
