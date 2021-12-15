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

package me.ahoo.cosid.shardingsphere.sharding.key;

import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ahoo wang
 */
class CosIdKeyGenerateAlgorithmTest {

    CosIdKeyGenerateAlgorithm cosIdKeyGenerateAlgorithm;

    @BeforeEach
    void init() {
        Properties properties = new Properties();
        properties.setProperty(CosIdAlgorithm.ID_NAME_KEY, IdGeneratorProvider.SHARE);
        cosIdKeyGenerateAlgorithm = new CosIdKeyGenerateAlgorithm();
        cosIdKeyGenerateAlgorithm.setProps(properties);
        cosIdKeyGenerateAlgorithm.init();
        DefaultIdGeneratorProvider.INSTANCE.setShare(AtomicLongGenerator.INSTANCE);
    }

    @Test
    void generateKey() {
        Object key = cosIdKeyGenerateAlgorithm.generateKey();
        assertNotNull(key);
    }
}
