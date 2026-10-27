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

package me.ahoo.cosid.accessor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.IdOverflowException;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.internal.AnnotatedIdFiller;
import me.ahoo.cosid.internal.Codecs;
import me.ahoo.cosid.internal.GeneratorEntry;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.PartsCodecSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class IdFillerTest {
    static CosIdRuntime runtime;
    static IdFiller filler;

    @BeforeAll
    static void start() {
        StoreProvider memory = new StoreProvider() {
            @Override
            public String type() {
                return "memory";
            }

            @Override
            public Store open(StoreConfig config) {
                return new InMemoryStore();
            }
        };
        BatchPolicy nearIntMax = new BatchPolicy(100, Integer.MAX_VALUE, Duration.ofSeconds(1), 100_000, Duration.ofSeconds(3), Optional.empty());
        RuntimeSpec spec = new RuntimeSpec("fill", Map.of("db", new StoreSpec("memory", Duration.ofSeconds(1), Map.of())),
            Optional.of(new MachineSpec.Leased("db", 10, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty(), Optional.empty())),
            Map.of(
                "default", new GeneratorSpec.Segment("db", BatchPolicy.DEFAULT, List.of()),
                "order", new GeneratorSpec.Segment("db", BatchPolicy.DEFAULT, List.of(new CodecSpec.Prefix("O"))),
                "big", new GeneratorSpec.Segment("db", nearIntMax, List.of()),
                "token", new GeneratorSpec.CosId80(ClockRegressionPolicy.DEFAULT, new PartsCodecSpec.Radix(62)),
                "bill", new GeneratorSpec.PeriodicSegment("db", Period.YEAR, ZoneOffset.UTC, BatchPolicy.DEFAULT, PeriodicPolicy.DEFAULT,
                    new GroupedCodecSpec.DatePrefix("-", List.of(new CodecSpec.Radix(10, 4, true)))),
                "off", new GeneratorSpec.Disabled(new GeneratorSpec.Segment("db", BatchPolicy.DEFAULT, List.of()))));
        RuntimeOptions defaults = RuntimeOptions.defaults();
        runtime = CosIdRuntime.start(spec, new RuntimeOptions(defaults.time(), defaults.executor(), defaults.listener(), List.of(memory),
            defaults.clients(), defaults.hostResolver(), defaults.port(), defaults.codecs()));
        filler = runtime.filler();
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    static class LongWithAccessors {
        @CosId
        private long id;
        int getterCalls;
        int setterCalls;

        public long getId() {
            getterCalls++;
            return id;
        }

        public void setId(long id) {
            setterCalls++;
            this.id = id;
        }
    }

    @Test
    void longThroughGetterAndSetter() {
        LongWithAccessors entity = new LongWithAccessors();
        filler.fill(entity);
        assertThat(entity.id, greaterThan(-1L));
        assertThat(entity.getterCalls, is(1));
        assertThat(entity.setterCalls, is(1));
        long first = entity.id;
        entity.id = 0;
        filler.fill(entity);
        assertThat(entity.id, is(first + 1));
    }

    static class LongField {
        @CosId("order")
        private long id = -1;
    }

    @Test
    void longFieldWithoutAccessors() {
        LongField entity = new LongField();
        filler.fill(entity);
        assertThat(entity.id, greaterThan(-1L));
    }

    static class Boxed {
        @CosId
        Long id;
        @CosId
        static Long ignoredStatic;
    }

    @Test
    void staticFieldsMustNotBeTheId() {
        IllegalStateException failed = assertThrows(IllegalStateException.class, () -> filler.fill(new Boxed()));
        assertThat(failed.getMessage(), containsString("must not be static"));
    }

    static class BoxedLong {
        @CosId
        private Long id;

        Long getId() {
            return id;
        }
    }

    @Test
    void boxedLongFilledOnlyWhenUnset() {
        BoxedLong entity = new BoxedLong();
        filler.fill(entity);
        assertThat(entity.id, greaterThan(-1L));
        BoxedLong preset = new BoxedLong();
        preset.id = 42L;
        filler.fill(preset);
        assertThat(preset.id, is(42L));
        preset.id = 0L;
        filler.fill(preset);
        assertThat(preset.id, greaterThan(0L));
    }

    static class IntField {
        @CosId("big")
        int id;
    }

    static class IntegerField {
        @CosId
        private Integer id;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }

    @Test
    void intsAreRangeChecked() {
        IntegerField boxed = new IntegerField();
        filler.fill(boxed);
        assertThat(boxed.id, greaterThan(-1));
        IntegerField preset = new IntegerField();
        preset.id = 7;
        filler.fill(preset);
        assertThat(preset.id, is(7));

        IntField first = new IntField();
        filler.fill(first);
        assertThat(first.id, is(Integer.MAX_VALUE));
        assertThrows(IdOverflowException.class, () -> filler.fill(new IntField()));
        IntField set = new IntField();
        set.id = 5;
        filler.fill(set);
        assertThat(set.id, is(5));
    }

    static class Strings {
        @CosId("order")
        String id = "";
    }

    static class TokenString {
        @CosId("token")
        private String token;

        public void setToken(String token) {
            this.token = token;
        }
    }

    static class BillString {
        @CosId("bill")
        String id;
    }

    @Test
    void stringsUseTheGeneratorCodec() {
        Strings order = new Strings();
        filler.fill(order);
        assertThat(order.id, startsWith("O"));
        Strings preset = new Strings();
        preset.id = "kept";
        filler.fill(preset);
        assertThat(preset.id, is("kept"));

        TokenString token = new TokenString();
        filler.fill(token);
        assertThat(runtime.get("token", me.ahoo.cosid.codec.PartsCodec.class).decode(token.token).node(),
            is(runtime.slot().orElseThrow().slot()));

        BillString bill = new BillString();
        filler.fill(bill);
        assertThat(bill.id, startsWith(Period.YEAR.keyOf(java.time.Instant.now(), ZoneOffset.UTC) + "-"));
    }

    @CosId(value = "order", field = "orderId")
    static class OnType {
        long orderId;
    }

    @CosId(field = "inherited")
    static class OnTypeNamingAnInheritedField extends Base {
    }

    static class Base {
        long inherited;
    }

    static class Parent {
        @CosId
        private long id;

        long id() {
            return id;
        }
    }

    static class Child extends Parent {
        String name;
    }

    @Test
    void typeTargetsAndSuperclasses() {
        OnType onType = new OnType();
        filler.fill(onType);
        assertThat(onType.orderId, greaterThan(-1L));
        OnTypeNamingAnInheritedField inherited = new OnTypeNamingAnInheritedField();
        inherited.inherited = 0;
        filler.fill(inherited);
        Child child = new Child();
        filler.fill(child);
        assertThat(child.id(), greaterThan(-1L));
        // A plain parent and its child are cached apart.
        Parent parent = new Parent();
        filler.fill(parent);
        assertThat(parent.id(), greaterThan(child.id()));
    }

    abstract static class Entity<ID> {
        @CosId
        private ID id;

        public ID getId() {
            return id;
        }

        public void setId(ID id) {
            this.id = id;
        }
    }

    static class User extends Entity<Long> {
    }

    abstract static class Middle<X> extends Entity<X> {
    }

    static class Document extends Middle<String> {
    }

    static class Raw<T extends Number> {
        @CosId
        T id;
    }

    @Test
    void genericFieldTypesResolveThroughSuperclasses() {
        User user = new User();
        filler.fill(user);
        assertThat(user.getId(), greaterThan(-1L));
        Document document = new Document();
        filler.fill(document);
        assertThat(Long.parseLong(document.getId()), greaterThan(-1L));
        IllegalStateException unresolved = assertThrows(IllegalStateException.class, () -> filler.fill(new Raw<Long>()));
        assertThat(unresolved.getMessage(), containsString("does not resolve to a class"));
    }

    static class Fluent {
        @CosId
        private long id;

        public long getId() {
            return id;
        }

        public Fluent setId(long id) {
            this.id = id;
            return this;
        }
    }

    @Test
    void fluentSetters() {
        Fluent fluent = new Fluent();
        filler.fill(fluent);
        assertThat(fluent.id, greaterThan(-1L));
    }

    static class Plain {
        long id;
    }

    @Test
    void entitiesWithoutAnIdAreLeftAsTheyAre() {
        Plain plain = new Plain();
        filler.fill(plain);
        assertThat(plain.id, is(0L));
        assertThrows(NullPointerException.class, () -> filler.fill(null));
    }

    static class TwoIds {
        @CosId
        long a;
        @CosId("order")
        long b;
    }

    @CosId(field = "nope")
    static class MissingField {
    }

    static class Unsupported {
        @CosId
        double id;
    }

    static class FinalId {
        @CosId
        final long id = 0;
    }

    static class UnknownGenerator {
        @CosId("nope")
        long id;
    }

    static class DisabledGenerator {
        @CosId("off")
        long id;
    }

    static class TokenAsLong {
        @CosId("token")
        long id;
    }

    static class ThrowingSetter {
        @CosId
        long id;

        public long getId() {
            return id;
        }

        public void setId(long id) throws Exception {
            throw new Exception("refused");
        }
    }

    @Test
    void invalidTargetsFail() {
        assertThat(assertThrows(IllegalStateException.class, () -> filler.fill(new TwoIds())).getMessage(),
            containsString("has more than one @CosId"));
        assertThat(assertThrows(IllegalStateException.class, () -> filler.fill(new MissingField())).getMessage(),
            containsString("names field [nope], which it does not have"));
        assertThat(assertThrows(IllegalStateException.class, () -> filler.fill(new Unsupported())).getMessage(),
            containsString("only long, Long, int, Integer and String are supported"));
        assertThat(assertThrows(IllegalStateException.class, () -> filler.fill(new FinalId())).getMessage(),
            containsString("is final and has no setter"));
        assertThrows(NoSuchElementException.class, () -> filler.fill(new UnknownGenerator()));
        assertThrows(GeneratorDisabledException.class, () -> filler.fill(new DisabledGenerator()));
        assertThrows(NoSuchElementException.class, () -> filler.fill(new TokenAsLong()));
        CosIdException refused = assertThrows(CosIdException.class, () -> filler.fill(new ThrowingSetter()));
        assertThat(refused.getCause().getMessage(), is("refused"));
    }

    @Test
    void nullStringIdIsFilled() {
        BillString bill = new BillString();
        assertThat(bill.id, nullValue());
        filler.fill(bill);
        assertThat(Radix.of(10, 4, true).decode(bill.id.substring(5)), greaterThan(-1L));
    }

    static class OtherLongId {
        @CosId
        private long id;
    }

    static class NoId {
        long id;
    }

    @Test
    void theAccessorOfAClassIsResolvedOnceAndCached() {
        List<String> lookups = new CopyOnWriteArrayList<>();
        ExecutorService refills = Executors.newVirtualThreadPerTaskExecutor();
        try (SegmentGenerator segment = new SegmentGenerator("default", "cached/default", new InMemoryStore(), BatchPolicy.DEFAULT, refills,
            TimeSource.SYSTEM, GeneratorListener.NOOP)) {
            GeneratorEntry entry = GeneratorEntry.segment(segment, Codecs.DECIMAL, "db", null);
            // The generator lookup is part of resolving an accessor: counting it counts the resolutions.
            AnnotatedIdFiller cached = new AnnotatedIdFiller(name -> {
                lookups.add(name);
                return entry;
            });
            long previous = -1;
            for (int i = 0; i < 5; i++) {
                LongWithAccessors entity = new LongWithAccessors();
                cached.fill(entity);
                assertThat(entity.id, greaterThan(previous));
                previous = entity.id;
            }
            assertThat(lookups, contains("default"));

            // Another class resolves its own accessor once.
            for (int i = 0; i < 3; i++) {
                OtherLongId other = new OtherLongId();
                cached.fill(other);
                assertThat(other.id, greaterThan(previous));
                previous = other.id;
            }
            assertThat(lookups, contains("default", "default"));

            // A class without an id is cached as "nothing to fill" and never looks up a generator.
            NoId none = new NoId();
            cached.fill(none);
            cached.fill(none);
            assertThat(none.id, is(0L));
            assertThat(lookups, contains("default", "default"));

            // The cache is per filler: a new one resolves again.
            new AnnotatedIdFiller(name -> {
                lookups.add("fresh:" + name);
                return entry;
            }).fill(new LongWithAccessors());
            assertThat(lookups, contains("default", "default", "fresh:default"));
        } finally {
            refills.shutdownNow();
        }
    }
}
