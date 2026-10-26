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

/**
 * CosId core: generation, codecs, the store SPI, the runtime and field filling. Exports the API packages only (D12);
 * {@code me.ahoo.cosid.internal} stays inside.
 */
module me.ahoo.cosid.core {
    requires org.slf4j;

    exports me.ahoo.cosid;
    exports me.ahoo.cosid.accessor;
    exports me.ahoo.cosid.codec;
    exports me.ahoo.cosid.lease;
    exports me.ahoo.cosid.observe;
    exports me.ahoo.cosid.runtime;
    exports me.ahoo.cosid.segment;
    exports me.ahoo.cosid.store;
    exports me.ahoo.cosid.time;

    uses me.ahoo.cosid.store.StoreProvider;
}
