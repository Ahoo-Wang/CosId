package me.ahoo.cosid.mybatis;

import com.google.common.collect.Lists;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.test.MockIdGenerator;

import lombok.SneakyThrows;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CosIdPluginTest .
 *
 * @author ahoo wang
 */
class CosIdPluginTest {
    private DefaultAccessorRegistry accessorRegistry;
    private CosIdPlugin cosIdPlugin;
    
    @BeforeEach
    void setup() {
        accessorRegistry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE));
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);
        accessorRegistry.register(Entity.class);
        cosIdPlugin = new CosIdPlugin(accessorRegistry);
    }
    
    @SneakyThrows
    @Test
    void intercept() {
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.INSERT)
            .build();
        Entity entity = new Entity();
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, entity});
        cosIdPlugin.intercept(invocation);
        Assertions.assertNotEquals(0, entity.getId());
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParmIsNull() {
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.INSERT)
            .build();
        Entity entity = null;
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, entity});
        cosIdPlugin.intercept(invocation);
    }
    
    @SneakyThrows
    @Test
    void interceptWhenSqlCommandTypeIsUnknown() {
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.UNKNOWN)
            .build();
        Entity entity = new Entity();
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, entity});
        cosIdPlugin.intercept(invocation);
        Assertions.assertEquals(0, entity.getId());
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamIsMap() {
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.INSERT)
            .build();
        Map<String, List<Entity>> param = new HashMap<>();
        List<Entity> list = Arrays.asList(new Entity(), new Entity());
        param.put(CosIdPlugin.DEFAULT_LIST_KEY, list);
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, param});
        cosIdPlugin.intercept(invocation);
        for (Entity entity : list) {
            Assertions.assertNotEquals(0, entity.getId());
        }
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamIsMapAndListIsNull() {
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.INSERT)
            .build();
        Map<String, List<Entity>> param = new HashMap<>();
        List<Entity> list = null;
        param.put(CosIdPlugin.DEFAULT_LIST_KEY, list);
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, param});
        cosIdPlugin.intercept(invocation);
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamIsMapAndCustomizeListKey() {
        final String listKey = "items";
        CosIdPlugin customizeListKey = new CosIdPlugin(accessorRegistry, listKey);
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.INSERT)
            .build();
        Map<String, List<Entity>> param = new HashMap<>();
        List<Entity> list = Arrays.asList(new Entity(), new Entity());
        param.put(listKey, list);
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, param});
        customizeListKey.intercept(invocation);
        for (Entity entity : list) {
            Assertions.assertNotEquals(0, entity.getId());
        }
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamMapNotFoundListKey() {
        CosIdPlugin plugin = new CosIdPlugin(accessorRegistry);
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), SqlCommandType.INSERT)
            .build();
        Map<String, List<Entity>> param = new MapperMethod.ParamMap<>();
        Invocation invocation = new Invocation(new InvocationTarget(), InvocationTarget.INVOKE_METHOD, new Object[] {statement, param});
        plugin.intercept(invocation);
    }
    
    public static class Entity {
        
        @CosId
        private long id;
        
        public long getId() {
            return id;
        }
        
        public void setId(long id) {
            this.id = id;
        }
    }
    
    public static class InvocationTarget implements Executor {
        
        public static final Method INVOKE_METHOD;
        
        static {
            try {
                INVOKE_METHOD = Executor.class.getMethod("update", MappedStatement.class, Object.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }


        @Override
        public int update(MappedStatement ms, Object parameter) throws SQLException {
            return 0;
        }

        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException {
            return Lists.newArrayList();
        }

        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
            return Lists.newArrayList();
        }

        @Override
        public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
            return null;
        }

        @Override
        public List<BatchResult> flushStatements() throws SQLException {
            return Lists.newArrayList();
        }

        @Override
        public void commit(boolean required) throws SQLException {

        }

        @Override
        public void rollback(boolean required) throws SQLException {

        }

        @Override
        public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
            return null;
        }

        @Override
        public boolean isCached(MappedStatement ms, CacheKey key) {
            return false;
        }

        @Override
        public void clearLocalCache() {

        }

        @Override
        public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {

        }

        @Override
        public Transaction getTransaction() {
            return null;
        }

        @Override
        public void close(boolean forceRollback) {

        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void setExecutorWrapper(Executor executor) {

        }
    }
}
