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

package me.ahoo.cosid.example.cosid.controller;

import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.cosid.CosIdState;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IdController.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping("ids")
public class IdController {
    private final IdGeneratorProvider provider;
    private final CosIdGenerator cosIdGenerator;
    
    public IdController(IdGeneratorProvider provider, CosIdGenerator cosIdGenerator) {
        this.provider = provider;
        this.cosIdGenerator = cosIdGenerator;
    }
    
    @GetMapping
    public String generateAsString() {
        return cosIdGenerator.generateAsString();
    }
    
    @GetMapping("/as-state")
    public CosIdState asState(String id) {
        return cosIdGenerator.getStateParser().asState(id);
    }
    
}
