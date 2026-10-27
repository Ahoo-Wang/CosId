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

package me.ahoo.cosid.store.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JsonTest {
    @Test
    void writesFlatObjectsWithEscapes() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("key", "a\"b\\c\nd\re\tf\u0001");
        fields.put("size", Long.MAX_VALUE);
        fields.put("flag", true);
        fields.put("none", null);
        fields.put("uuid", java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertEquals("{\"key\":\"a\\\"b\\\\c\\nd\\re\\tf\\u0001\",\"size\":9223372036854775807,\"flag\":true,\"none\":null,"
            + "\"uuid\":\"00000000-0000-0000-0000-000000000001\"}", Json.write(fields));
        assertEquals("{}", Json.write(Map.of()));
    }

    @Test
    void readsNestedValuesAndExactLongs() {
        Map<String, Object> object = Json.readObject(" { \"a\" : 9007199254740993 , \"b\":[1, -2.5e3, \"x\", true, false, null, {}, []],"
            + " \"c\":{\"d\":\"\\u0041\\b\\f\\n\\r\\t\\/\\\"\"}, \"e\": -7 } ");
        assertEquals(9007199254740993L, object.get("a"));
        assertEquals(Arrays.asList(1L, new BigDecimal("-2.5e3"), "x", true, false, null, Map.of(), List.of()), object.get("b"));
        assertEquals(Map.of("d", "A\b\f\n\r\t/\""), object.get("c"));
        assertEquals(-7L, object.get("e"));
    }

    @Test
    void rejectsWhatIsNotAJsonObject() {
        for (String bad : List.of("", "[]", "{} x", "{\"a\"}", "{\"a\":}", "{\"a\":1,}", "{a:1}", "{\"a\":\"x", "{\"a\":\"\\u12\"}",
            "{\"a\":tru}", "{\"a\":1-2}", "{\"a\":[1 2]}", "{\"a\":\"x\\", "<html>" + "x".repeat(300))) {
            assertThrows(IllegalArgumentException.class, () -> Json.readObject(bad), bad);
        }
    }
}
