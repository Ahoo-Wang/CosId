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


package me.ahoo.cosid.jackson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.codec.Friendly;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.jackson.AsString.Codec;
import me.ahoo.cosid.jackson.AsString.Preset;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.tck.TestRuntimes;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneId;

class AsStringTest {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final long ID = Layout.SNOWFLAKE_MS.pack(123_456_789L, 5, 42);
    static CosIdRuntime runtime;
    static JsonMapper mapper;

    @BeforeAll
    static void start() {
        runtime = TestRuntimes.start("jackson");
        mapper = JsonMapper.builder().addModule(new CosIdJacksonModule(runtime)).build();
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    public static class Inline {
        @AsString
        public long radix;
        @AsString(codec = Codec.TO_STRING)
        public Long text;
        @AsString(radix = 36, width = 13, padStart = false)
        public long radix36;
        @AsString(codec = Codec.FRIENDLY, zone = "Asia/Shanghai")
        public long friendly;
        @AsString(codec = Codec.FRIENDLY, layout = Preset.SNOWFLAKE_S, zone = "Asia/Shanghai", padStart = false)
        public long friendlySeconds;
        @AsString(codec = Codec.FRIENDLY, timeBits = 41, nodeBits = 5, seqBits = 17, epoch = 1_700_000_000_000L, zone = "UTC")
        public long friendlyBits;
        @AsString(codec = Codec.FRIENDLY, layout = Preset.JS_SAFE_MS, epoch = 1_700_000_000_000L, zone = "UTC")
        public long friendlyEpoch;
        @AsString(codec = Codec.FRIENDLY)
        public long friendlySystemZone;
        @AsString
        public Long absent;
    }

    public static class ByGenerator {
        @AsString(generator = "order")
        public long order;
    }

    public static class Mixed {
        @AsString(generator = "order", radix = 10)
        public long order;
    }

    public static class HalfBits {
        @AsString(codec = Codec.FRIENDLY, timeBits = 41)
        public long id;
    }

    @Test
    void roundTripsEveryInlineCodec() {
        Inline ids = new Inline();
        ids.radix = ID;
        ids.text = ID;
        ids.radix36 = ID;
        ids.friendly = ID;
        ids.friendlySeconds = Layout.SNOWFLAKE_S.pack(99_999L, 3, 7);
        Layout bits = new Layout(Instant.ofEpochMilli(1_700_000_000_000L), Layout.SNOWFLAKE_MS.unit(), 41, 5, 17, Layout.defaultSeqResetThreshold(17));
        ids.friendlyBits = bits.pack(1_000L, 31, 100_000L);
        Layout jsSafe = new Layout(Instant.ofEpochMilli(1_700_000_000_000L), Layout.JS_SAFE_MS.unit(), 41, 3, 9, Layout.defaultSeqResetThreshold(9));
        ids.friendlyEpoch = jsSafe.pack(5_000L, 1, 2);
        ids.friendlySystemZone = ID;

        JsonNode json = mapper.valueToTree(ids);
        assertThat(json.get("radix").asString(), is(Radix.of(62, 11, true).encode(ID)));
        assertThat(json.get("text").asString(), is(Long.toString(ID)));
        assertThat(json.get("radix36").asString(), is(Radix.of(36, 13, false).encode(ID)));
        assertThat(json.get("friendly").asString(), is(Friendly.of(Layout.SNOWFLAKE_MS, SHANGHAI, true).encode(ID)));
        assertThat(json.get("friendlySeconds").asString(), is(Friendly.of(Layout.SNOWFLAKE_S, SHANGHAI, false).encode(ids.friendlySeconds)));
        assertThat(json.get("friendlyBits").asString(), is(Friendly.of(bits, ZoneId.of("UTC"), true).encode(ids.friendlyBits)));
        assertThat(json.get("friendlyEpoch").asString(), is(Friendly.of(jsSafe, ZoneId.of("UTC"), true).encode(ids.friendlyEpoch)));
        assertThat(json.get("friendlySystemZone").asString(), is(Friendly.of(Layout.SNOWFLAKE_MS, ZoneId.systemDefault(), true).encode(ID)));
        assertThat(json.get("absent").isNull(), is(true));

        Inline back = mapper.treeToValue(json, Inline.class);
        assertThat(back.radix, is(ID));
        assertThat(back.text, is(ID));
        assertThat(back.radix36, is(ID));
        assertThat(back.friendly, is(ID));
        assertThat(back.friendlySeconds, is(ids.friendlySeconds));
        assertThat(back.friendlyBits, is(ids.friendlyBits));
        assertThat(back.friendlyEpoch, is(ids.friendlyEpoch));
        assertThat(back.friendlySystemZone, is(ID));
        assertThat(back.absent, nullValue());
    }

    @Test
    void inlineCodecsNeedNoModule() {
        JsonMapper plain = JsonMapper.builder().build();
        Inline ids = new Inline();
        ids.radix = ID;
        String json = plain.writeValueAsString(ids);
        assertThat(json, containsString("\"radix\":\"" + Radix.of(62, 11, true).encode(ID) + "\""));
        assertThat(plain.readValue(json, Inline.class).radix, is(ID));
    }

    @Test
    void generatorCodec() {
        IdCodec codec = runtime.get(TestRuntimes.ORDER, IdCodec.class);
        ByGenerator order = new ByGenerator();
        order.order = 1234;
        String json = mapper.writeValueAsString(order);
        assertThat(json, is("{\"order\":\"" + codec.encode(1234) + "\"}"));
        assertThat(json, containsString("\"O"));
        assertThat(mapper.readValue(json, ByGenerator.class).order, is(1234L));
    }

    @Test
    void generatorNeedsTheModule() {
        JsonMapper plain = JsonMapper.builder().build();
        InvalidDefinitionException error = assertThrows(InvalidDefinitionException.class, () -> plain.writeValueAsString(new ByGenerator()));
        assertThat(error.getMessage(), containsString("CosIdJacksonModule"));
        assertThrows(InvalidDefinitionException.class, () -> plain.readValue("{\"order\":\"O1\"}", ByGenerator.class));
    }

    @Test
    void invalidAnnotations() {
        assertThat(assertThrows(InvalidDefinitionException.class, () -> mapper.writeValueAsString(new Mixed())).getMessage(),
            containsString("leave the inline attributes unset"));
        assertThat(assertThrows(InvalidDefinitionException.class, () -> mapper.readValue("{\"id\":\"1\"}", HalfBits.class)).getMessage(),
            containsString("together"));
    }

    @Test
    void readsNumbersTheCodecCouldWrite() {
        assertThat(mapper.readValue("{\"radix\":42}", Inline.class).radix, is(42L));
        assertThrows(InvalidFormatException.class, () -> mapper.readValue("{\"radix\":-1}", Inline.class));
        assertThat(mapper.readValue("{\"text\":-7}", Inline.class).text, is(-7L));
    }

    @Test
    void readsEmptyAsNullAndRejectsInvalidText() {
        assertThat(mapper.readValue("{\"text\":\"\"}", Inline.class).text, nullValue());
        assertThat(mapper.readValue("{\"text\":null}", Inline.class).text, nullValue());
        InvalidFormatException invalid = assertThrows(InvalidFormatException.class, () -> mapper.readValue("{\"radix\":\"!!\"}", Inline.class));
        assertThat(invalid.getMessage(), startsWith("Cannot deserialize value of type `java.lang.Long` from String \"!!\""));
        assertThrows(MismatchedInputException.class, () -> mapper.readValue("{\"radix\":true}", Inline.class));
    }

    @Test
    void decimalWithoutAProperty() {
        AsStringSerializer serializer = new AsStringSerializer();
        assertThat(serializer.createContextual(null, null), is(serializer));
        assertThat(serializer.handledType(), is(Long.class));
        AsStringDeserializer deserializer = new AsStringDeserializer();
        assertThat(deserializer.createContextual(null, null), is(deserializer));
        assertThat(deserializer.handledType(), is(Long.class));
        assertThat(AsStringCodecs.TO_STRING.decode(AsStringCodecs.TO_STRING.encode(-5)), is(-5L));
    }

    @Test
    void moduleIdentity() {
        CosIdJacksonModule module = new CosIdJacksonModule(name -> AsStringCodecs.TO_STRING);
        assertThat(module.getModuleName(), is("cosid"));
        assertThat(module.version().isUnknownVersion(), is(true));
    }
}
