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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ArgsTest {
    @Test
    void parsesValuesFlagsAndPairs() {
        Args args = Args.parse(List.of("--plan", "p.json", "--confirm-stopped", "--observe=30s", "--to-option", "user=sa", "--to-option", "password=x"),
            Set.of("confirm-stopped"), Set.of("plan", "confirm-stopped", "observe", "to-option", "gap"));
        assertThat(args.get("plan"), is("p.json"));
        assertThat(args.flag("confirm-stopped"), is(true));
        assertThat(args.duration("observe", Duration.ZERO), is(Duration.ofSeconds(30)));
        assertThat(args.pairs("to-option"), is(Map.of("user", "sa", "password", "x")));
        assertThat(args.number("gap", 7), is(7L));
        assertThrows(IllegalArgumentException.class, () -> args.required("gap"));
    }

    @Test
    void rejectsWhatItDoesNotKnow() {
        assertThrows(IllegalArgumentException.class, () -> Args.parse(List.of("--nope", "1"), Set.of(), Set.of("plan")));
        assertThrows(IllegalArgumentException.class, () -> Args.parse(List.of("plan"), Set.of(), Set.of("plan")));
        assertThrows(IllegalArgumentException.class, () -> Args.parse(List.of("--plan"), Set.of(), Set.of("plan")));
        Args pairs = Args.parse(List.of("--rename", "x"), Set.of(), Set.of("rename"));
        assertThrows(IllegalArgumentException.class, () -> pairs.pairs("rename"));
    }

    @Test
    void durations() {
        assertThat(Args.parseDuration("PT2M"), is(Duration.ofMinutes(2)));
        assertThat(Args.parseDuration("250ms"), is(Duration.ofMillis(250)));
        assertThat(Args.parseDuration("10s"), is(Duration.ofSeconds(10)));
        assertThat(Args.parseDuration("2m"), is(Duration.ofMinutes(2)));
        assertThat(Args.parseDuration("1h"), is(Duration.ofHours(1)));
        assertThat(Args.parseDuration("0"), is(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Args.parseDuration("soon"));
    }
}
