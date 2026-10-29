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


package me.ahoo.cosid.spring.data.jdbc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.tck.internal.TestRuntimes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.TransactionManager;

import java.util.UUID;

/** Spring Data JDBC repositories on H2 with the callback: {@code @Id} and {@code @CosId} ids are filled on insert. */
class CosIdBeforeConvertCallbackTest {
    static CosIdRuntime runtime;
    static AnnotationConfigApplicationContext context;

    public static class Account {
        @Id
        private Long id;
        private String name;

        public Account(String name) {
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }

    public record Ticket(@Id String id, String name) {
        public Ticket withId(String id) {
            return new Ticket(id, name);
        }
    }

    public static class Invoice {
        @Id
        @CosId(TestRuntimes.ORDER)
        private long id;
        private int seat;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public int getSeat() {
            return seat;
        }
    }

    public static class Seat {
        @Id
        private Integer id;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }

    public static class Token {
        @Id
        private UUID id;

        public UUID getId() {
            return id;
        }
    }

    public static class NoId {
        private String name;

        public String getName() {
            return name;
        }
    }

    public interface Accounts extends CrudRepository<Account, Long> {
    }

    public interface Tickets extends CrudRepository<Ticket, String> {
    }

    public interface Invoices extends CrudRepository<Invoice, Long> {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJdbcRepositories(considerNestedRepositories = true, basePackageClasses = CosIdBeforeConvertCallbackTest.class)
    static class JdbcConfig extends AbstractJdbcConfiguration {
        @Bean
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
                .addScript("classpath:jdbc/schema.sql").build();
        }

        @Bean
        NamedParameterJdbcOperations namedParameterJdbcOperations(EmbeddedDatabase dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        TransactionManager transactionManager(EmbeddedDatabase dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        CosIdBeforeConvertCallback cosIdBeforeConvertCallback(JdbcMappingContext mappingContext) {
            return new CosIdBeforeConvertCallback(runtime, mappingContext);
        }
    }

    @BeforeAll
    static void start() {
        runtime = TestRuntimes.start("data-jdbc");
        context = new AnnotationConfigApplicationContext(JdbcConfig.class);
    }

    @AfterAll
    static void close() {
        context.close();
        runtime.close();
    }

    @Test
    void idPropertyFromTheDefaultGenerator() {
        Account saved = context.getBean(Accounts.class).save(new Account("alice"));
        assertThat(saved.getId(), greaterThan(0L));
        assertThat(context.getBean(Accounts.class).findById(saved.getId()).orElseThrow().getName(), is("alice"));
    }

    @Test
    void immutableAggregateComesBackFilled() {
        Ticket saved = context.getBean(Tickets.class).save(new Ticket(null, "t"));
        assertThat(saved.id(), matchesPattern("\\d+"));
        assertThat(context.getBean(Tickets.class).existsById(saved.id()), is(true));
    }

    @Test
    void cosIdTakesPrecedence() {
        Invoices invoices = context.getBean(Invoices.class);
        long before = invoices.count();
        Invoice saved = invoices.save(new Invoice());
        assertThat(saved.getId(), greaterThan(0L));
        assertThat(runtime.get(TestRuntimes.ORDER, me.ahoo.cosid.codec.IdCodec.class).encode(saved.getId()), startsWith("O"));
        assertThat(invoices.count(), is(before + 1));
    }

    @Test
    void setIdsAndOtherTypesAreLeftAlone() {
        CosIdBeforeConvertCallback callback = context.getBean(CosIdBeforeConvertCallback.class);
        Account account = new Account("set");
        account.setId(7L);
        assertThat(callback.onBeforeConvert(account), sameInstance(account));
        assertThat(account.getId(), is(7L));
        Token token = new Token();
        assertThat(callback.onBeforeConvert(token), sameInstance(token));
        NoId noId = new NoId();
        assertThat(callback.onBeforeConvert(noId), sameInstance(noId));
    }

    @Test
    void intIdAndNamedGenerator() {
        JdbcMappingContext mappingContext = context.getBean(JdbcMappingContext.class);
        Seat seat = (Seat) new CosIdBeforeConvertCallback(runtime, mappingContext, TestRuntimes.ORDER).onBeforeConvert(new Seat());
        assertThat(seat.getId(), not(is((Integer) null)));
        Ticket ticket = (Ticket) new CosIdBeforeConvertCallback(runtime, mappingContext, TestRuntimes.BILL).onBeforeConvert(new Ticket("", "b"));
        assertThat(ticket.id(), matchesPattern("\\d{14}"));
    }
}
