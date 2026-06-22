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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class SpringRedisMachineIdDistributorScriptTest {

    @Test
    void distributeBySelfShouldNeverLowerLastStamp() throws IOException {
        List<String> lines = lines(loadDistributeScript());
        int selfStart = lines.indexOf("--DistributeBySelf");
        int revertStart = lines.indexOf("--DistributeByRevert");

        assertThat(selfStart, greaterThanOrEqualTo(0));
        assertThat(revertStart, greaterThanOrEqualTo(selfStart));
        List<String> selfBlock = lines.subList(selfStart, revertStart);
        assertThat(selfBlock, equalTo(List.of(
            "--DistributeBySelf",
            "local machineState = redis.call('hget', instanceIdxKey, instanceId)",
            "if machineState then",
            "local states = convertStingToState(machineState);",
            "local machineId = states[1];",
            "local lastStamp = states[2];",
            "if lastStamp < currentStamp then",
            "lastStamp = currentStamp;",
            "end",
            "setState(machineId, lastStamp);",
            "return { machineId, lastStamp }",
            "end",
            ""
        )));
    }

    private static String loadDistributeScript() throws IOException {
        return StreamUtils.copyToString(
            SpringRedisMachineIdDistributor.MACHINE_ID_DISTRIBUTE_SOURCE.getInputStream(),
            StandardCharsets.UTF_8
        );
    }

    private static List<String> lines(String script) {
        return Arrays.stream(script.split("\n", -1))
            .map(String::trim)
            .collect(Collectors.toList());
    }
}
