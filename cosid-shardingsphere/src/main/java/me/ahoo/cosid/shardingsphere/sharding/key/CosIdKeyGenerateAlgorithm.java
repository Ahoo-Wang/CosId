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

package me.ahoo.cosid.shardingsphere.sharding.key;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.provider.LazyIdGenerator;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;

import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmPostProcessor;
import org.apache.shardingsphere.sharding.spi.KeyGenerateAlgorithm;
import org.apache.shardingsphere.spi.typed.TypedSPI;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Properties;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class CosIdKeyGenerateAlgorithm implements TypedSPI, ShardingSphereAlgorithmPostProcessor, KeyGenerateAlgorithm {

    public static final String TYPE = CosId.COSID.toUpperCase();

    public static final String AS_STRING_KEY = "as-string";

    /**
     * Need to declare volatile here ?
     */
    private volatile Properties props = new Properties();

    protected volatile LazyIdGenerator lazyIdGenerator;
    private volatile boolean asString;

    /**
     * Get properties.
     *
     * @return properties
     */
    @Override
    public Properties getProps() {
        return props;
    }

    /**
     * Set properties.
     *
     * @param props properties
     */
    @Override
    public void setProps(Properties props) {
        this.props = props;
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        lazyIdGenerator = new LazyIdGenerator(getProps().getOrDefault(CosIdAlgorithm.ID_NAME_KEY, IdGeneratorProvider.SHARE).toString());
        String asStringStr = getProps().getProperty(AS_STRING_KEY, Boolean.FALSE.toString());
        asString = Boolean.parseBoolean(asStringStr);
        lazyIdGenerator.tryGet(false);
    }

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
        if (asString) {
            return lazyIdGenerator.generateAsString();
        }
        return lazyIdGenerator.generate();
    }

}
