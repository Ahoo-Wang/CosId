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

package me.ahoo.cosid.cosid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class RadixCosIdStateParserTest {

    static Stream<Arguments> parserProvider() {
        return Stream.of(
            arguments(RadixCosIdStateParser.ofRadix62(8, 4, 4), new CosIdState(61, 7, 15), "0z7F"),
            arguments(RadixCosIdStateParser.ofRadix36(8, 4, 4), new CosIdState(35, 7, 15), "0Z7F")
        );
    }

    @ParameterizedTest
    @MethodSource("parserProvider")
    void asStringShouldEncodeFixedWidthTimestampMachineAndSequence(RadixCosIdStateParser parser, CosIdState state, String expected) {
        assertEquals(expected, parser.asString(state));
        assertEquals(expected, parser.asString(state.getTimestamp(), state.getMachineId(), state.getSequence()));
    }

    @ParameterizedTest
    @MethodSource("parserProvider")
    void asStateShouldRoundTripFixedWidthIds(RadixCosIdStateParser parser, CosIdState expected, String id) {
        assertEquals(expected, parser.asState(id));
    }

    @ParameterizedTest
    @MethodSource("parserProvider")
    void asStateShouldRejectIdsWhoseLengthDoesNotMatchConfiguredComponents(RadixCosIdStateParser parser, CosIdState ignored, String id) {
        IllegalArgumentException tooShort = assertThrows(IllegalArgumentException.class, () -> parser.asState(id.substring(1)));
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class, () -> parser.asState(id + "0"));

        assertEquals("id[" + id.substring(1) + "] length must equal to totalCharSize:[4].", tooShort.getMessage());
        assertEquals("id[" + id + "0] length must equal to totalCharSize:[4].", tooLong.getMessage());
    }
}
