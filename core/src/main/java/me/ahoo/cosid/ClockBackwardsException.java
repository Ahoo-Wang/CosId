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

/**
 * The clock went back further than the {@code ClockRegressionPolicy} allows (03-core-model.md §4).
 */
public class ClockBackwardsException extends CosIdException {
    private final String generator;
    private final long backwardsMillis;

    public ClockBackwardsException(String generator, long backwardsMillis) {
        super("Clock moved backwards by " + backwardsMillis + " ms in generator [" + generator + "]");
        this.generator = generator;
        this.backwardsMillis = backwardsMillis;
    }

    public String generator() {
        return generator;
    }

    public long backwardsMillis() {
        return backwardsMillis;
    }
}
