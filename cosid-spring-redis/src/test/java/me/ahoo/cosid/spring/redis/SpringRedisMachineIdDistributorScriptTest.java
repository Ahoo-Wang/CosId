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

package me.ahoo.cosid.spring.redis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class SpringRedisMachineIdDistributorScriptTest {

    @Test
    void distributeBySelfShouldNotLowerLastStamp() throws IOException {
        String script = StreamUtils.copyToString(
            SpringRedisMachineIdDistributor.MACHINE_ID_DISTRIBUTE_SOURCE.getInputStream(),
            StandardCharsets.UTF_8
        );
        int selfStart = script.indexOf("--DistributeBySelf");
        int revertStart = script.indexOf("--DistributeByRevert");
        String selfBlock = script.substring(selfStart, revertStart);

        Assertions.assertTrue(selfBlock.contains("local lastStamp = states[2];"));
        Assertions.assertTrue(selfBlock.contains("if lastStamp < currentStamp then"));
        Assertions.assertTrue(selfBlock.contains("lastStamp = currentStamp;"));
        Assertions.assertTrue(selfBlock.contains("setState(machineId, lastStamp);"));
        Assertions.assertTrue(selfBlock.contains("return { machineId, lastStamp }"));
    }
}
