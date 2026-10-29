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

package me.ahoo.cosid.spring.autoconfigure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.HostResolver;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.time.TimeBasedGenerator;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.mongodb.MongoDBContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link CosIdAutoConfiguration} in a context (07-runtime-and-spring.md §4): stores and strategies, customizers,
 * disabled generators, bean names and types, clients, listeners, shutdown order and startup failures.
 */
@ExtendWith(OutputCaptureExtension.class)
class CosIdAutoConfigurationTest {
    static final String[] MIXED = {
        "cosid.namespace=mixed",
        "cosid.stores.mem.type=memory",
        "cosid.stores.other.type=memory",
        "cosid.machine.leased.store=mem",
        "cosid.generators.default.strategy=snowflake",
        "cosid.generators.order.strategy=segment",
        "cosid.generators.order.store=other",
        "cosid.generators.order.codec[0].radix.radix=62",
        "cosid.generators.order.codec[1].prefix=O",
        "cosid.generators.trace.strategy=snowflake",
        "cosid.generators.trace.codec[0].friendly.zone=Asia/Shanghai",
        "cosid.generators.bill.strategy=periodic-segment",
        "cosid.generators.bill.store=mem",
        "cosid.generators.bill.period=YEAR_MONTH_DAY",
        "cosid.generators.bill.zone=Asia/Shanghai",
        "cosid.generators.bill.codec[0].date-prefix.delimiter=",
        "cosid.generators.bill.codec[1].radix.radix=10",
        "cosid.generators.bill.codec[1].radix.width=6",
        "cosid.generators.bill.codec[1].radix.pad-start=true",
        "cosid.generators.token.strategy=cosid80",
        "cosid.generators.token.codec[0].radix.radix=62",
    };

    static final ApplicationContextRunner RUNNER = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMetricsConfiguration.class, CosIdActuatorConfiguration.class));

    static ApplicationContextRunner withStores() {
        return RUNNER.withBean(MemoryStores.class, MemoryStores::new);
    }

    @Test
    void startsMixedStoresAndStrategiesWithABeanPerGenerator() {
        withStores().withPropertyValues(MIXED).run(context -> {
            assertNull(context.getStartupFailure());
            CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
            assertEquals("mixed", runtime.namespace());
            assertEquals(List.of("bill", "default", "order", "token", "trace"), List.copyOf(runtime.describe().keySet()));

            assertSame(runtime.generator("default"), context.getBean(IdGenerator.class), "the default generator is primary");
            assertSame(runtime.generator("default"), context.getBean("default"));
            assertSame(runtime.generator("order"), context.getBean("order", IdGenerator.class));
            assertSame(runtime.generator("trace"), context.getBean("trace", IdGenerator.class));
            assertSame(runtime.get("token", TimeBasedGenerator.class), context.getBean("token", PartsIdGenerator.class));
            assertSame(runtime.periodic("bill"), context.getBean("bill", GroupedIdGenerator.class));
            assertEquals(List.of("bill"), List.of(context.getBeanNamesForType(GroupedIdGenerator.class)));
            assertEquals(List.of("token"), List.of(context.getBeanNamesForType(PartsIdGenerator.class)));
            assertEquals(List.of("default", "order", "trace"), List.of(context.getBeanNamesForType(IdGenerator.class)).stream().sorted().toList());

            IdGenerator order = context.getBean("order", IdGenerator.class);
            long first = order.generate();
            assertThat(order.generate(), greaterThan(first));
            assertThat(runtime.stringGenerator("order").generate(), startsWith("O"));
            assertThat(context.getBean("bill", GroupedIdGenerator.class).generate().period().length(), org.hamcrest.Matchers.is(8));
            assertTrue(runtime.slot().isPresent());

            Entity entity = new Entity();
            context.getBean(IdFiller.class).fill(entity);
            assertThat(entity.id, greaterThan(0L));

            MemoryStores stores = context.getBean(MemoryStores.class);
            assertEquals(List.of("open mem", "open other"), stores.events);
            assertTrue(stores.configs.stream().allMatch(config -> config.client().isEmpty()));
        });
    }

    @Test
    void aDottedGeneratorNameInBracketsInYamlIsTheGeneratorOfThatName() {
        // Wow's <context>.<aggregate>: the YAML key "[eshop-ordering.order]" keeps the dot, and the unknown-key check passes.
        String yaml = """
            cosid:
              namespace: shop
              stores:
                mem:
                  type: memory
              generators:
                "[eshop-ordering.order]":
                  strategy: segment
                  store: mem
            """;
        withStores().withInitializer(context -> {
            try {
                for (PropertySource<?> source : new YamlPropertySourceLoader().load("dotted", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))) {
                    context.getEnvironment().getPropertySources().addFirst(source);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).run(context -> {
            assertNull(context.getStartupFailure());
            CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
            assertEquals(List.of("eshop-ordering.order"), List.copyOf(runtime.describe().keySet()));
            IdGenerator order = runtime.generator("eshop-ordering.order");
            assertSame(order, runtime.get("eshop-ordering.order", IdGenerator.class));
            assertSame(order, context.getBean("eshop-ordering.order", IdGenerator.class));
            long first = order.generate();
            assertThat(order.generate(), greaterThan(first));
            assertFalse(runtime.stringGenerator("eshop-ordering.order").generate().isEmpty());
        });
    }

    @Test
    void encodesAndDecodesThePeriodWhereTheCodecListPutsIt() {
        withStores().withPropertyValues("cosid.stores.mem.type=memory",
            "cosid.generators.after.strategy=periodic-segment", "cosid.generators.after.store=mem", "cosid.generators.after.period=YEAR",
            "cosid.generators.after.codec[0].radix.radix=10", "cosid.generators.after.codec[0].radix.width=4",
            "cosid.generators.after.codec[0].radix.pad-start=true",
            "cosid.generators.after.codec[1].period.delimiter=-", "cosid.generators.after.codec[2].prefix=BIZ-",
            "cosid.generators.none.strategy=periodic-segment", "cosid.generators.none.store=mem", "cosid.generators.none.period=YEAR",
            "cosid.generators.none.codec[0].period.enabled=false", "cosid.generators.none.codec[1].prefix=N").run(context -> {
                assertNull(context.getStartupFailure());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                GroupedId after = runtime.periodic("after").generate();
                GroupedCodec afterCodec = runtime.get("after", GroupedCodec.class);
                String text = afterCodec.encode(after);
                assertEquals("BIZ-" + after.period() + "-" + String.format("%04d", after.value()), text);
                assertEquals(after, afterCodec.decode(text));

                GroupedId none = runtime.periodic("none").generate();
                GroupedCodec noneCodec = runtime.get("none", GroupedCodec.class);
                assertEquals("N" + none.value(), noneCodec.encode(none));
                UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> noneCodec.decode("N" + none.value()));
                assertThat(e.getMessage(), containsString("the period is not in the text"));
            });
    }

    @Test
    void appliesCustomizersInOrderAndRegistersWhatTheyAdd() {
        withStores().withUserConfiguration(Customizers.class).withPropertyValues(
            "cosid.stores.mem.type=memory",
            "cosid.generators.gone.strategy=segment",
            "cosid.generators.gone.store=mem",
            "cosid.generators.kept.strategy=segment",
            "cosid.generators.kept.store=mem").run(context -> {
                assertNull(context.getStartupFailure());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertEquals(List.of("extra", "kept"), List.copyOf(runtime.describe().keySet()));
                assertFalse(context.containsBean("gone"));
                assertSame(runtime.generator("extra"), context.getBean("extra", IdGenerator.class));
                assertEquals(List.of("add", "remove"), Customizers.CALLS);
                RuntimeSpec spec = context.getBean(CosIdGeneratorRegistrar.SPEC_BEAN, RuntimeSpec.class);
                assertEquals(List.of("extra", "kept"), spec.generators().keySet().stream().sorted().toList());
            });
    }

    @Test
    void aDisabledGeneratorKeepsItsBeanAndThrowsOnUse() {
        withStores().withPropertyValues(
            "cosid.stores.mem.type=memory",
            "cosid.generators.default.strategy=snowflake",
            "cosid.generators.default.disabled=true",
            "cosid.generators.order.strategy=segment",
            "cosid.generators.order.store=mem",
            "cosid.generators.order.disabled=true",
            "cosid.generators.token.strategy=cosid80",
            "cosid.generators.token.disabled=true",
            "cosid.generators.bill.strategy=periodic-segment",
            "cosid.generators.bill.disabled=true").run(context -> {
                assertNull(context.getStartupFailure(), "no machine or store is needed");
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertTrue(runtime.slot().isEmpty());
                assertEquals(Map.of("disabled", true), runtime.describe().get("order").details());
                IdGenerator ids = context.getBean("default", IdGenerator.class);
                assertThrows(GeneratorDisabledException.class, ids::generate);
                assertThrows(GeneratorDisabledException.class, context.getBean("order", IdGenerator.class)::generate);
                assertThrows(GeneratorDisabledException.class, context.getBean("token", PartsIdGenerator.class)::nextParts);
                assertThrows(GeneratorDisabledException.class, context.getBean("bill", GroupedIdGenerator.class)::generate);
                assertEquals(PartsIdGenerator.class, context.getType("token"));
                assertEquals(GroupedIdGenerator.class, context.getType("bill"));
                assertTrue(context.getBean(MemoryStores.class).events.isEmpty(), "the store of a disabled generator is not opened");
            });
    }

    @Test
    void anEmptyCodecItemIsItsDefaultsInThePropertiesBeanToo() {
        // R1-spring-10: YAML "- friendly: {}" on every supported Spring Boot (4.0 converts the empty text).
        withStores().withPropertyValues("cosid.namespace=app", "cosid.machine.static.slot=1", "cosid.generators.g.strategy=snowflake",
            "cosid.generators.g.codec[0].friendly=").run(context -> {
                assertNull(context.getStartupFailure());
                assertNotNull(context.getBean(CosIdProperties.class).generators().get("g").codec().get(0).friendly());
            });
    }

    @Test
    void theMasterSwitchTurnsEverythingOff() {
        withStores().withPropertyValues("cosid.enabled=false", "cosid.generators.default.strategy=snowflake").run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean(CosIdGeneratorRegistrar.RUNTIME_BEAN));
            assertFalse(context.containsBean("default"));
        });
    }

    @Test
    void closesTheRuntimeAfterItsUsersAndBeforeItsClients() {
        List<String> events = new CopyOnWriteArrayList<>();
        MemoryStores stores = new MemoryStores(events);
        RUNNER.withBean(MemoryStores.class, () -> stores)
            .withBean("client", Client.class, () -> new Client(events))
            .withUserConfiguration(UserConfiguration.class)
            .withPropertyValues("cosid.stores.mem.type=memory", "cosid.stores.mem.client=client",
                "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertThat(stores.configs.getFirst().client().orElseThrow(), instanceOf(Client.class));
                context.getBean(UserConfiguration.Closer.class).events = events;
                context.close();
                assertEquals(List.of("open mem", "user closed", "close mem", "client closed"), events);
            });
    }

    @Test
    void usesTheSingleOrPrimaryClientOfAStoreType() {
        RUNNER.withBean(MemoryStores.class, MemoryStores::new).withUserConfiguration(DataSources.class)
            .withPropertyValues("cosid.stores.db.type=jdbc", "cosid.stores.db.options.initSchema=true",
                "cosid.generators.order.strategy=segment", "cosid.generators.order.store=db")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertThat(context.getBean("order", IdGenerator.class).generate(), greaterThan(-1L));
                assertThat(DataSources.tables(context.getBean("primary", javax.sql.DataSource.class)), greaterThan(0));
                assertEquals(0, DataSources.tables(context.getBean("secondary", javax.sql.DataSource.class)));
            });
    }

    @Test
    void usesTheCuratorFrameworkBeanOfTheContextForZookeeperStores() throws Exception {
        try (TestingServer server = new TestingServer(true);
             CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(100))) {
            client.start();
            assertTrue(client.blockUntilConnected(30, java.util.concurrent.TimeUnit.SECONDS), "connected to the test server");
            // A client the application owns: registered without a destroy method, so only CosId could close it.
            RUNNER.withBean(CuratorFramework.class, () -> client, definition -> definition.setDestroyMethodName(""))
                .withPropertyValues("cosid.namespace=zk-client", "cosid.stores.zk.type=zookeeper",
                    "cosid.generators.order.strategy=segment", "cosid.generators.order.store=zk")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    IdGenerator order = context.getBean("order", IdGenerator.class);
                    long first = order.generate();
                    assertThat(order.generate(), greaterThan(first));
                    assertFalse(client.getChildren().forPath("/cosid/range").isEmpty(), "the store wrote its range through the context's client");
                });
            assertEquals(CuratorFrameworkState.STARTED, client.getState(), "the context's CosId beans leave the client open");
            assertNotNull(client.checkExists().forPath("/cosid/range"), "and it still works");
        }
    }

    @Test
    void usesTheMongoClientBeanOfTheContextForMongoStores() {
        String external = System.getenv("COSID_TEST_MONGODB_URI");
        MongoDBContainer container = null;
        String uri = external;
        if (external == null || external.isBlank()) {
            String image = System.getenv("COSID_TEST_MONGODB_IMAGE");
            container = new MongoDBContainer(image == null || image.isBlank() ? "mongo:7.0" : image).withReplicaSet();
            container.start();
            uri = container.getReplicaSetUrl();
        }
        String database = "cosid_spring_" + java.util.UUID.randomUUID().toString().replace("-", "");
        try (MongoClient client = MongoClients.create(uri)) {
            // A client the application owns: registered without a destroy method, so only CosId could close it.
            RUNNER.withBean(MongoClient.class, () -> client, definition -> definition.setDestroyMethodName(""))
                .withPropertyValues("cosid.namespace=mongo-client", "cosid.stores.mongo.type=mongo",
                    "cosid.stores.mongo.options.database=" + database,
                    "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mongo")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    IdGenerator order = context.getBean("order", IdGenerator.class);
                    long first = order.generate();
                    assertThat(order.generate(), greaterThan(first));
                    assertThat(client.getDatabase(database).getCollection("cosid_range").countDocuments(), greaterThan(0L));
                });
            assertEquals(1.0, client.getDatabase(database).runCommand(new Document("ping", 1)).get("ok", Number.class).doubleValue(),
                "the context's CosId beans leave the client open");
            client.getDatabase(database).drop();
        } finally {
            if (container != null) {
                container.stop();
            }
        }
    }

    @Test
    void resolvesCustomCodecsByBeanNameAndClassName() {
        withStores().withBean("upper", IdCodec.class, () -> new Hex())
            .withPropertyValues("cosid.stores.mem.type=memory",
                "cosid.generators.a.strategy=segment", "cosid.generators.a.store=mem", "cosid.generators.a.codec[0].custom=upper",
                "cosid.generators.b.strategy=segment", "cosid.generators.b.store=mem",
                "cosid.generators.b.codec[0].custom=" + Hex.class.getName())
            .run(context -> {
                assertNull(context.getStartupFailure());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertThat(runtime.stringGenerator("a").generate(), startsWith("x"));
                assertThat(runtime.stringGenerator("b").generate(), startsWith("x"));
            });
    }

    @Test
    void derivesTheOwnerKeyFromTheHostResolverAndServerPort() {
        withStores().withBean(HostResolver.class, () -> () -> "pod-a")
            .withPropertyValues("server.port=8080", "cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem",
                "cosid.generators.default.strategy=snowflake")
            .run(context -> assertEquals("pod-a:8080", context.getBean(CosIdRuntime.class).slot().orElseThrow().owner().key()));
        withStores().withPropertyValues("server.port=0", "cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem",
                "cosid.generators.default.strategy=snowflake")
            .run(context -> assertThat(context.getBean(CosIdRuntime.class).slot().orElseThrow().owner().key(),
                endsWith(":" + ProcessHandle.current().pid())));
    }

    @Test
    void takesTheOwnerKeyHostFromSpringCloudInetUtils() {
        withStores().withConfiguration(AutoConfigurations.of(UtilAutoConfiguration.class))
            .withPropertyValues("server.port=8081", "cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem",
                "cosid.generators.default.strategy=snowflake")
            .run(context -> {
                assertNull(context.getStartupFailure());
                String ip = context.getBean(InetUtils.class).findFirstNonLoopbackHostInfo().getIpAddress();
                assertEquals(ip, context.getBean(HostResolver.class).host());
                assertEquals(ip + ":8081", context.getBean(CosIdRuntime.class).slot().orElseThrow().owner().key());
            });
        withStores().withConfiguration(AutoConfigurations.of(UtilAutoConfiguration.class)).withBean(HostResolver.class, () -> () -> "own")
            .withPropertyValues("server.port=8081", "cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem",
                "cosid.generators.default.strategy=snowflake")
            .run(context -> assertEquals("own:8081", context.getBean(CosIdRuntime.class).slot().orElseThrow().owner().key()));
    }

    @Test
    void everyListenerBeanHearsEventsDespiteAFailingOne() {
        AtomicInteger acquired = new AtomicInteger();
        GeneratorListener failing = new GeneratorListener() {
            @Override
            public void onSlotAcquired(SlotLease lease, String reason) {
                throw new IllegalStateException("listener failure");
            }
        };
        GeneratorListener counting = new GeneratorListener() {
            @Override
            public void onSlotAcquired(SlotLease lease, String reason) {
                acquired.incrementAndGet();
            }
        };
        withStores().withBean("failing", GeneratorListener.class, () -> failing).withBean("counting", GeneratorListener.class, () -> counting)
            .withPropertyValues("cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem", "cosid.generators.default.strategy=snowflake")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(1, acquired.get());
            });
    }

    @Test
    void forwardsEveryEventOfTheCompositeListener() {
        List<String> heard = new CopyOnWriteArrayList<>();
        GeneratorListener recording = RecordingListener.of(heard);
        GeneratorListener composite = CosIdAutoConfiguration.CompositeListener.of(List.of(recording, recording));
        RecordingListener.fire(composite);
        assertEquals(2 * RecordingListener.EVENTS, heard.size());
        assertSame(GeneratorListener.NOOP, CosIdAutoConfiguration.CompositeListener.of(List.of()));
        assertSame(recording, CosIdAutoConfiguration.CompositeListener.of(List.of(recording)));
    }

    @Test
    void failsStartupWithTheProblemsOfTheConfiguration() {
        withStores().withPropertyValues("cosid.generators.a.strategy=uuid", "cosid.generators.b.strategy=segment").run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            String message = root(failure).getMessage();
            assertThat(message, containsString("Invalid cosid.* configuration"));
            assertThat(message, containsString("cosid.generators.a.strategy: one of snowflake, cosid80, segment, periodic-segment, custom, not [uuid]"));
            assertThat(message, containsString("cosid.generators.b.store: required"));
        });
    }

    @Test
    void failsStartupWithTheProblemsOfTheSpec() {
        withStores().withPropertyValues("cosid.stores.db.type=nope", "cosid.generators.default.strategy=snowflake",
            "cosid.generators.order.strategy=segment", "cosid.generators.order.store=db").run(context -> {
                String message = root(context.getStartupFailure()).getMessage();
                assertThat(message, containsString("Invalid RuntimeSpec of namespace [cosid]"));
                assertThat(message, containsString("need a machine slot"));
                assertThat(message, containsString("no StoreProvider of type [nope]"));
            });
    }

    @Test
    void failsStartupWhenAGeneratorNameIsTaken() {
        withStores().withBean("order", String.class, () -> "taken")
            .withPropertyValues("cosid.stores.mem.type=memory", "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem")
            .run(context -> assertThat(root(context.getStartupFailure()).getMessage(),
                containsString("The bean name [order] of the CosId generator [order] is taken by another bean")));
    }

    @Test
    void theNamespaceComesFromTheApplicationName(CapturedOutput output) {
        // D38: cosid.namespace, else spring.application.name, else cosid with a warning.
        String warning = "cosid.namespace and spring.application.name are both unset";
        withStores().withPropertyValues("spring.application.name=orders", "cosid.stores.mem.type=memory", "cosid.generators.order.strategy=segment",
            "cosid.generators.order.store=mem").run(context -> assertEquals("orders", context.getBean(CosIdRuntime.class).namespace()));
        withStores().withPropertyValues("spring.application.name=orders", "cosid.namespace=billing", "cosid.stores.mem.type=memory",
            "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem")
            .run(context -> assertEquals("billing", context.getBean(CosIdRuntime.class).namespace()));
        assertFalse(output.getAll().contains(warning));
        withStores().withPropertyValues("cosid.stores.mem.type=memory", "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem")
            .run(context -> assertEquals("cosid", context.getBean(CosIdRuntime.class).namespace()));
        assertTrue(output.getAll().contains(warning));
        withStores().withPropertyValues("spring.application.name=Order Service").run(context ->
            assertThat(root(context.getStartupFailure()).getMessage(), containsString("spring.application.name [Order Service] is no valid namespace")));
    }

    @Test
    void anUnknownKeyFailsTheStart() {
        // R1-spring-03.
        withStores().withPropertyValues("cosid.stores.mem.type=memory", "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem",
            "cosid.generators.order.stpe=10").run(context -> {
                assertNotNull(context.getStartupFailure());
                StringBuilder messages = new StringBuilder();
                for (Throwable current = context.getStartupFailure(); current != null; current = current.getCause()) {
                    messages.append(current.getMessage());
                }
                assertThat(messages.toString(), containsString("cosid.generators.order.stpe"));
            });
        // System properties and environment variables are shared with the rest of the process: not checked.
        withStores().withSystemProperties("cosid.example.server.jar=build/libs/x.jar").withPropertyValues("cosid.stores.mem.type=memory",
            "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem").run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void aCustomGeneratorIsABeanByName() {
        // D41: the runtime hands the bean out and does not close it.
        AtomicInteger closed = new AtomicInteger();
        withStores().withBean("legacyIds", IdGenerator.class, () -> new LegacyIds(closed))
            .withPropertyValues("cosid.generators.legacy.strategy=custom", "cosid.generators.legacy.ref=legacyIds",
                "cosid.generators.legacy.codec[0].prefix=L")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(1001L, context.getBean("legacy", IdGenerator.class).generate());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertEquals("L1002", runtime.stringGenerator("legacy").generate());
                assertEquals("custom", runtime.describe().get("legacy").strategy());
                // Closing the runtime leaves the bean open: the context closes its own bean.
                runtime.close();
                assertEquals(0, closed.get());
            });
        assertEquals(1, closed.get());
        withStores().withPropertyValues("cosid.generators.legacy.strategy=custom", "cosid.generators.legacy.ref=nothing").run(context ->
            assertThat(root(context.getStartupFailure()).getMessage(), containsString("generator [legacy]: no generator [nothing] to refer to")));
    }

    /** A user generator; the bean closes it, the runtime never. */
    static final class LegacyIds implements IdGenerator, AutoCloseable {
        private final AtomicInteger next = new AtomicInteger(1000);
        private final AtomicInteger closed;

        LegacyIds(AtomicInteger closed) {
            this.closed = closed;
        }

        @Override
        public long generate() {
            return next.incrementAndGet();
        }

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    @Test
    void namedMachinesFromTheProperties() {
        // D37.
        withStores().withPropertyValues("cosid.namespace=order-service", "cosid.stores.mem.type=memory",
                "cosid.machine.leased.store=mem",
                "cosid.machines.global.namespace=global-ids", "cosid.machines.global.leased.store=mem", "cosid.machines.global.leased.slot-offset=512",
                "cosid.generators.trace.strategy=snowflake", "cosid.generators.token.strategy=cosid80", "cosid.generators.token.machine=global")
            .run(context -> {
                assertNull(context.getStartupFailure());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertEquals(java.util.Set.of("default", "global"), runtime.machines());
                assertEquals("order-service", runtime.slot().orElseThrow().namespace());
                assertEquals("global-ids", runtime.slot("global").orElseThrow().namespace());
                assertEquals(512, context.getBean("token", PartsIdGenerator.class).nextParts().node());
            });
    }

    @Test
    void aCustomizerReturningNothingFailsStartup() {
        withStores().withBean(RuntimeSpecCustomizer.class, () -> spec -> null).run(context ->
            assertThat(root(context.getStartupFailure()).getMessage(), containsString("returned no spec")));
    }

    static Throwable root(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** An entity filled by the {@link IdFiller} bean. */
    static class Entity {
        @CosId("order")
        long id;
    }

    /** A codec by bean and by class name. */
    public static final class Hex implements IdCodec {
        @Override
        public String encode(long id) {
            return "x" + Long.toHexString(id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text.substring(1), 16);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Customizers {
        static final List<String> CALLS = new CopyOnWriteArrayList<>();

        @Bean
        @Order(1)
        static RuntimeSpecCustomizer add() {
            return spec -> {
                CALLS.add("add");
                return spec.toBuilder().generator("extra", GeneratorSpec.Segment.builder("mem").batch(BatchPolicy.DEFAULT).codec(List.of()).build()).build();
            };
        }

        @Bean
        @Order(2)
        static RuntimeSpecCustomizer remove() {
            return spec -> {
                CALLS.add("remove");
                assertTrue(spec.generators().containsKey("extra"), "runs after add");
                return spec.toBuilder().removeGenerator("gone").build();
            };
        }
    }

    /** A store client the context owns, closed by it. */
    record Client(List<String> events) implements AutoCloseable {
        @Override
        public void close() {
            events.add("client closed");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserConfiguration {
        /** A bean using a generator bean, closed by the context. */
        static final class Closer implements AutoCloseable {
            volatile List<String> events = new CopyOnWriteArrayList<>();
            final IdGenerator order;

            Closer(IdGenerator order) {
                this.order = order;
            }

            @Override
            public void close() {
                order.generate();
                events.add("user closed");
            }
        }

        @Bean
        Closer closer(@Qualifier("order") IdGenerator order) {
            return new Closer(order);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSources {
        @Bean
        javax.sql.DataSource secondary() {
            return dataSource("secondary");
        }

        @Bean
        @Primary
        javax.sql.DataSource primary() {
            return dataSource("primary");
        }

        static javax.sql.DataSource dataSource(String name) {
            org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
            return dataSource;
        }

        static int tables(javax.sql.DataSource dataSource) throws java.sql.SQLException {
            try (java.sql.Connection connection = dataSource.getConnection();
                 java.sql.ResultSet tables = connection.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
                tables.next();
                return tables.getInt(1);
            }
        }
    }
}
