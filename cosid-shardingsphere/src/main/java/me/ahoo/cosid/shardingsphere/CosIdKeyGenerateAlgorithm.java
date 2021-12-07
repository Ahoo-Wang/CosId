/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosid.shardingsphere;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.IdGenerator;
import org.apache.shardingsphere.sharding.spi.KeyGenerateAlgorithm;

import javax.annotation.concurrent.ThreadSafe;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class CosIdKeyGenerateAlgorithm extends AbstractCosIdAlgorithm implements KeyGenerateAlgorithm {

    public static final String TYPE = CosId.COSID.toUpperCase();

    /**
     * Get type.
     *
     * @return type
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Generate key.
     *
     * @return generated key
     */
    @Override
    public Comparable<?> generateKey() {
        final IdGenerator idGenerator = this.tryGetIdGenerator(true);
        return idGenerator.generate();
    }

}
