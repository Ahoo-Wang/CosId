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

package me.ahoo.cosid.axon;

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.provider.LazyIdGenerator;

import org.axonframework.common.IdentifierFactory;

/**
 * CosId Identifier Factory .
 *
 * @author ahoo wang
 */
public class CosIdIdentifierFactory extends IdentifierFactory {
    public static final String ID_KEY = "axon.cosid";
    private static final String ID_NAME;
    private static final LazyIdGenerator ID_GENERATOR;
    
    static {
        ID_NAME = System.getProperty(ID_KEY, IdGeneratorProvider.SHARE);
        ID_GENERATOR = new LazyIdGenerator(ID_NAME);
    }
    
    @Override
    public String generateIdentifier() {
        return ID_GENERATOR.generateAsString();
    }
}
