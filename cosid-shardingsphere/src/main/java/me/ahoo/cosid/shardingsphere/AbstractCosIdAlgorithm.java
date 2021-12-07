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
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmPostProcessor;
import org.apache.shardingsphere.spi.typed.TypedSPI;

import java.util.Optional;
import java.util.Properties;

/**
 * @author ahoo wang
 */
public abstract class AbstractCosIdAlgorithm implements TypedSPI, ShardingSphereAlgorithmPostProcessor {

    /**
     * #{@link me.ahoo.cosid.provider.IdGeneratorProvider#get(String)}
     */
    public static final String ID_NAME_KEY = "id-name";

    private Properties props = new Properties();

    private volatile IdGenerator idGenerator;

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

    private String getIdName() {
        return getProps().getOrDefault(ID_NAME_KEY, IdGeneratorProvider.SHARE).toString();
    }

    protected IdGenerator tryGetIdGenerator(boolean required) {
        if (idGenerator != null) {
            return idGenerator;
        }

        String idName = getIdName();
        Optional<IdGenerator> idGeneratorOp = DefaultIdGeneratorProvider.INSTANCE.get(idName);
        if (idGeneratorOp.isPresent()) {
            idGenerator = idGeneratorOp.get();
            return idGenerator;
        } else if (required) {
            throw new CosIdException(Strings.lenientFormat("CosId:[%s] Not Found!"));
        }
        return null;
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        tryGetIdGenerator(false);
    }

}
