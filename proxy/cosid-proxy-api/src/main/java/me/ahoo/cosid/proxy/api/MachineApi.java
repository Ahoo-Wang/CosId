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

package me.ahoo.cosid.proxy.api;

import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("machines/{namespace}")
public interface MachineApi {
    @PostExchange
    MachineStateResponse distribute(@PathVariable String namespace,
                            @RequestParam int machineBit,
                            @RequestParam String instanceId,
                            @RequestParam boolean stable,
                            @RequestParam String safeGuardDuration)
        throws MachineIdOverflowException;

    @DeleteExchange("machines")
    void revert(@PathVariable String namespace,
                @RequestParam String instanceId,
                @RequestParam boolean stable);

    @PatchExchange
    void guard(@PathVariable String namespace,
               @RequestParam String instanceId,
               @RequestParam boolean stable,
               @RequestParam String safeGuardDuration) throws MachineIdLostException;
}
