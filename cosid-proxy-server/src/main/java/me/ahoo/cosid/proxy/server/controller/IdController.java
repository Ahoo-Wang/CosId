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

package me.ahoo.cosid.proxy.server.controller;

import me.ahoo.cosid.provider.IdGeneratorProvider;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ID resource controller .
 * <p>
 * Important: Due to network IO performance problems caused by high request frequency,
 * it is not recommended to use this method to obtain IDs.
 * </p>
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping("ids")
public class IdController {
    private final IdGeneratorProvider provider;
    
    public IdController(IdGeneratorProvider provider) {
        this.provider = provider;
    }
    
    @Operation(summary = "Generate a ID by share.")
    @GetMapping
    public long generate() {
        return provider
            .getShare()
            .generate();
    }
    
    @Operation(summary = "Generate a ID by id name.")
    @GetMapping("{name}")
    public long generate(@PathVariable String name) {
        return provider
            .getRequired(name)
            .generate();
    }
    
    @Operation(summary = "Generate a ID as String by share.")
    @GetMapping("/as-string")
    public String generateAsString() {
        return provider
            .getShare()
            .generateAsString();
    }
    
    @Operation(summary = "Generate a ID as String by id name.")
    @GetMapping("/as-string/{name}")
    public String generateAsString(@PathVariable String name) {
        return provider
            .getRequired(name)
            .generateAsString();
    }
}
