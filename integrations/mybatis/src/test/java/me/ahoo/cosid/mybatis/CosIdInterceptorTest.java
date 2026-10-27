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


package me.ahoo.cosid.mybatis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.tck.TestRuntimes;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MyBatis on H2 with the interceptor: single and batch inserts get their ids, other statements do not. */
class CosIdInterceptorTest {
    static CosIdRuntime runtime;
    static SqlSessionFactory sessions;

    public static class Order {
        @CosId(TestRuntimes.ORDER)
        private String id;
        private String name;

        public Order() {
        }

        Order(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public interface OrderMapper {
        @Update("create table t_order (id varchar(32) primary key, name varchar(32))")
        void create();

        @Insert("insert into t_order (id, name) values (#{id}, #{name})")
        int insert(Order order);

        @Insert("<script>insert into t_order (id, name) values "
            + "<foreach collection='list' item='o' separator=','>(#{o.id}, #{o.name})</foreach></script>")
        int insertAll(List<Order> orders);

        @Insert("<script>insert into t_order (id, name) values "
            + "<foreach collection='orders' item='o' separator=','>(#{o.id}, #{o.name})</foreach></script>")
        int insertNamed(@Param("orders") List<Order> orders, @Param("tag") String tag);

        @Insert("insert into t_order (id, name) values (#{id}, #{name})")
        int insertMap(Map<String, Object> row);

        @Update("update t_order set name = #{name} where id = #{id}")
        int update(Order order);

        @Select("select count(*) from t_order where id like 'O%'")
        long countFilled();
    }

    @BeforeAll
    static void start() {
        runtime = TestRuntimes.start("mybatis");
        sessions = factory("orders", CosIdInterceptor.DEFAULT_LIST_KEY);
        try (SqlSession session = sessions.openSession(true)) {
            session.getMapper(OrderMapper.class).create();
        }
    }

    static SqlSessionFactory factory(String database, String listKey) {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1", "sa", "");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(OrderMapper.class);
        configuration.addInterceptor(new CosIdInterceptor(runtime.filler(), listKey));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    @Test
    void insertFillsTheEntity() {
        Order order = new Order("one");
        try (SqlSession session = sessions.openSession(true)) {
            assertThat(session.getMapper(OrderMapper.class).insert(order), is(1));
        }
        assertThat(order.getId(), startsWith("O"));
    }

    @Test
    void batchInsertFillsEachEntity() {
        List<Order> orders = List.of(new Order("a"), new Order("b"), new Order("c"));
        try (SqlSession session = sessions.openSession(true)) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            long before = mapper.countFilled();
            assertThat(mapper.insertAll(orders), is(3));
            assertThat(mapper.countFilled(), is(before + 3));
        }
        assertThat(orders.stream().map(Order::getId).distinct().count(), is(3L));
    }

    @Test
    void configurableListKey() {
        assertThat(new CosIdInterceptor(runtime.filler()).listKey(), is(CosIdInterceptor.DEFAULT_LIST_KEY));
        assertThat(new CosIdInterceptor(runtime.filler(), "orders").listKey(), is("orders"));
        SqlSessionFactory named = factory("named", "orders");
        List<Order> orders = new ArrayList<>(List.of(new Order("x"), new Order("y")));
        try (SqlSession session = named.openSession(true)) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            mapper.create();
            assertThat(mapper.insertNamed(orders, "t"), is(2));
            assertThat(mapper.countFilled(), is(2L));
        }
    }

    @Test
    void otherStatementsAndParametersAreLeftAlone() {
        Order order = new Order("kept");
        order.setId("");
        try (SqlSession session = sessions.openSession(true)) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            assertThat(mapper.update(order), is(0));
            assertThat(order.getId(), is(""));
            assertThat(mapper.insertMap(Map.of("id", "X1", "name", "map")), is(1));
        }
    }
}
