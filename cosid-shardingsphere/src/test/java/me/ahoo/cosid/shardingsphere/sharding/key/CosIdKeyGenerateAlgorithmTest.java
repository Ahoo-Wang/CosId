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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import me.ahoo.cosid.util.MockIdGenerator;
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
        assertNotNull(cosIdKeyGenerateAlgorithm.generateKey());
    }


    @Test
    public void generateKeyWhenNotSetIdName() {
        DefaultSegmentId defaultSegmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        DefaultIdGeneratorProvider.INSTANCE.setShare(defaultSegmentId);
        CosIdKeyGenerateAlgorithm keyGenerateAlgorithm = new CosIdKeyGenerateAlgorithm();
        Properties properties = new Properties();
        keyGenerateAlgorithm.setProps(properties);
        keyGenerateAlgorithm.init();
        assertEquals(1L, cosIdKeyGenerateAlgorithm.generateKey());
        assertEquals(2L, cosIdKeyGenerateAlgorithm.generateKey());
    }

    @Test
    public void assertGenerateKeyWhenIdProviderIsEmpty() {
        DefaultIdGeneratorProvider.INSTANCE.clear();
        CosIdKeyGenerateAlgorithm keyGenerateAlgorithm = new CosIdKeyGenerateAlgorithm();
        Properties properties = new Properties();
        keyGenerateAlgorithm.setProps(properties);
        keyGenerateAlgorithm.init();
        assertThrows(CosIdException.class, keyGenerateAlgorithm::generateKey);
    }


    @Test
    void generateKeyAsString() {
        String idName = "stringId";
        Properties properties = new Properties();
        properties.setProperty(CosIdAlgorithm.ID_NAME_KEY, idName);
        properties.setProperty(CosIdKeyGenerateAlgorithm.AS_STRING_KEY, "true");
        CosIdKeyGenerateAlgorithm stringCosIdKeyAlg = new CosIdKeyGenerateAlgorithm();
        stringCosIdKeyAlg.setProps(properties);
        stringCosIdKeyAlg.init();
        DefaultIdGeneratorProvider.INSTANCE.set(idName, MockIdGenerator.INSTANCE);

        Comparable<?> key = stringCosIdKeyAlg.generateKey();
        Assertions.assertTrue(key instanceof String);
        Assertions.assertTrue(key.toString().startsWith("test_"));
    }

}
