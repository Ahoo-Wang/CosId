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

import com.google.common.base.Strings;
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.apache.shardingsphere.sharding.spi.KeyGenerateAlgorithm;

import java.util.Optional;
import java.util.Properties;

/**
 * @author ahoo wang
 */
public class CosIdKeyGenerateAlgorithm implements KeyGenerateAlgorithm {
    public static final String ID_NAME_KEY = "id-name";
    public static final String TYPE = CosId.COSID.toUpperCase();

    private volatile IdGenerator idGenerator;
    private Properties props = new Properties();
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
     * Get type.
     *
     * @return type
     */
    @Override
    public String getType() {
        return TYPE;
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

    private String getIdName() {
        return getProps().getOrDefault(ID_NAME_KEY, IdGeneratorProvider.SHARE).toString();
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        initIdGen(false);
    }

    private void initIdGen(boolean required) {
        if (idGenerator != null) {
            return;
        }

        String idName = getIdName();
        Optional<IdGenerator> idGeneratorOp = DefaultIdGeneratorProvider.INSTANCE.get(idName);
        if (idGeneratorOp.isPresent()) {
            idGenerator = idGeneratorOp.get();
        } else if (required) {
            throw new CosIdException(Strings.lenientFormat("CosId:[%s] Not Found!"));
        }
    }

    /**
     * Generate key.
     *
     * @return generated key
     */
    @Override
    public Comparable<?> generateKey() {
        this.initIdGen(true);
        return idGenerator.generate();
    }

}
