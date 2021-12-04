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

package me.ahoo.cosid.example.controller;

import me.ahoo.cosid.example.entity.FriendlyIdEntity;
import me.ahoo.cosid.example.entity.LongIdEntity;
import me.ahoo.cosid.example.repository.EntityRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping("test")
public class EntityController {

    private final EntityRepository entityRepository;

    public EntityController(EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @PostMapping("/long")
    public LongIdEntity longId() {
        LongIdEntity entity = new LongIdEntity();
        entityRepository.insert(entity);
        /**
         * {
         *   "id": 208796080181248
         * }
         */
        return entity;
    }

    @PostMapping("/friendly")
    public FriendlyIdEntity friendly() {
        FriendlyIdEntity entity = new FriendlyIdEntity();
        entityRepository.insertFriendly(entity);
        return entity;
    }
}
