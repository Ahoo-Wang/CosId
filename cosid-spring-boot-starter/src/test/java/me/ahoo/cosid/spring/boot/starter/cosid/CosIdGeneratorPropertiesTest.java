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

package me.ahoo.cosid.spring.boot.starter.cosid;

import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.DEFAULT_SEQUENCE_BIT;
import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.DEFAULT_SEQUENCE_RESET_THRESHOLD;
import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.DEFAULT_TIMESTAMP_BIT;
import static me.ahoo.cosid.cosid.RadixCosIdGenerator.DEFAULT_MACHINE_BIT;
import static me.ahoo.cosid.spring.boot.starter.CosIdProperties.DEFAULT_NAMESPACE;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CosIdGeneratorPropertiesTest {
    
    @Test
    void isEnabled() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        Assertions.assertFalse(properties.isEnabled());
    }
    
    @Test
    void setEnabled() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        properties.setEnabled(true);
        Assertions.assertTrue(properties.isEnabled());
    }
    
    @Test
    void getNamespace() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        Assertions.assertEquals(DEFAULT_NAMESPACE, properties.getNamespace());
    }
    
    @Test
    void setNamespace() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        String namespace = "wow cosid";
        properties.setNamespace(namespace);
        Assertions.assertEquals(namespace, properties.getNamespace());
    }
    
    @Test
    void getMachineBit() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        Assertions.assertEquals(DEFAULT_MACHINE_BIT, properties.getMachineBit());
    }
    
    @Test
    void setMachineBit() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        int machineBit = 12;
        properties.setMachineBit(machineBit);
        Assertions.assertEquals(machineBit, properties.getMachineBit());
    }
    
    @Test
    void getTimestampBit() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        Assertions.assertEquals(DEFAULT_TIMESTAMP_BIT, properties.getTimestampBit());
    }
    
    @Test
    void setTimestampBit() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        int timestampBit = 50;
        properties.setTimestampBit(50);
        Assertions.assertEquals(timestampBit, properties.getTimestampBit());
    }
    
    @Test
    void getSequenceBit() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        Assertions.assertEquals(DEFAULT_SEQUENCE_BIT, properties.getSequenceBit());
    }
    
    @Test
    void setSequenceBit() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        int sequenceBit = 18;
        properties.setSequenceBit(18);
        Assertions.assertEquals(sequenceBit, properties.getSequenceBit());
    }
    
    @Test
    void getSequenceResetThreshold() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        Assertions.assertEquals(DEFAULT_SEQUENCE_RESET_THRESHOLD, properties.getSequenceResetThreshold());
    }
    
    @Test
    void setSequenceResetThreshold() {
        CosIdGeneratorProperties properties = new CosIdGeneratorProperties();
        int sequenceResetThreshold = 1000000;
        properties.setSequenceResetThreshold(sequenceResetThreshold);
        Assertions.assertEquals(sequenceResetThreshold, properties.getSequenceResetThreshold());
    }
}
