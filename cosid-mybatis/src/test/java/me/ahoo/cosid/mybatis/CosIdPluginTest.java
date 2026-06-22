package me.ahoo.cosid.mybatis;

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
import org.junit.jupiter.api.AfterEach;
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
    private Configuration configuration;
    
    @BeforeEach
    void setup() {
        configuration = new Configuration();
        accessorRegistry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE));
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);
        accessorRegistry.register(Entity.class);
        cosIdPlugin = new CosIdPlugin(accessorRegistry);
    }

    @AfterEach
    void destroy() {
        DefaultIdGeneratorProvider.INSTANCE.clear();
    }
    
    @SneakyThrows
    @Test
    void intercept() {
        MappedStatement statement = statement(SqlCommandType.INSERT);
        Entity entity = new Entity();
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, entity);

        Object result = cosIdPlugin.intercept(invocation);

        assertProceeded(target, result, statement, entity);
        Assertions.assertNotEquals(0, entity.getId());
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParmIsNull() {
        MappedStatement statement = statement(SqlCommandType.INSERT);
        Entity entity = null;
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, entity);

        Object result = cosIdPlugin.intercept(invocation);

        assertProceeded(target, result, statement, entity);
    }
    
    @SneakyThrows
    @Test
    void interceptWhenSqlCommandTypeIsUnknown() {
        MappedStatement statement = statement(SqlCommandType.UNKNOWN);
        Entity entity = new Entity();
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, entity);

        Object result = cosIdPlugin.intercept(invocation);

        assertProceeded(target, result, statement, entity);
        Assertions.assertEquals(0, entity.getId());
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamIsMap() {
        MappedStatement statement = statement(SqlCommandType.INSERT);
        Map<String, List<Entity>> param = new HashMap<>();
        List<Entity> list = Arrays.asList(new Entity(), new Entity());
        param.put(CosIdPlugin.DEFAULT_LIST_KEY, list);
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, param);

        Object result = cosIdPlugin.intercept(invocation);

        assertProceeded(target, result, statement, param);
        for (Entity entity : list) {
            Assertions.assertNotEquals(0, entity.getId());
        }
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamIsMapAndListIsNull() {
        MappedStatement statement = statement(SqlCommandType.INSERT);
        Map<String, List<Entity>> param = new HashMap<>();
        List<Entity> list = null;
        param.put(CosIdPlugin.DEFAULT_LIST_KEY, list);
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, param);

        Object result = cosIdPlugin.intercept(invocation);

        assertProceeded(target, result, statement, param);
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamIsMapAndCustomizeListKey() {
        final String listKey = "items";
        CosIdPlugin customizeListKey = new CosIdPlugin(accessorRegistry, listKey);
        MappedStatement statement = statement(SqlCommandType.INSERT);
        Map<String, List<Entity>> param = new HashMap<>();
        List<Entity> list = Arrays.asList(new Entity(), new Entity());
        param.put(listKey, list);
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, param);

        Object result = customizeListKey.intercept(invocation);

        assertProceeded(target, result, statement, param);
        for (Entity entity : list) {
            Assertions.assertNotEquals(0, entity.getId());
        }
    }
    
    @SneakyThrows
    @Test
    void interceptWhenParamMapNotFoundListKey() {
        CosIdPlugin plugin = new CosIdPlugin(accessorRegistry);
        MappedStatement statement = statement(SqlCommandType.INSERT);
        Map<String, List<Entity>> param = new MapperMethod.ParamMap<>();
        InvocationTarget target = new InvocationTarget();
        Invocation invocation = invocation(target, statement, param);

        Object result = plugin.intercept(invocation);

        assertProceeded(target, result, statement, param);
    }

    private MappedStatement statement(SqlCommandType sqlCommandType) {
        return new MappedStatement.Builder(configuration, "intercept", new StaticSqlSource(configuration, ""), sqlCommandType)
            .build();
    }

    private Invocation invocation(InvocationTarget target, MappedStatement statement, Object parameter) {
        return new Invocation(target, InvocationTarget.INVOKE_METHOD, new Object[] {statement, parameter});
    }

    private void assertProceeded(InvocationTarget target, Object result, MappedStatement statement, Object parameter) {
        Assertions.assertEquals(target.result, result);
        Assertions.assertEquals(1, target.updateCount);
        Assertions.assertSame(statement, target.mappedStatement);
        Assertions.assertSame(parameter, target.parameter);
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
        private final int result = 1;
        private int updateCount;
        private MappedStatement mappedStatement;
        private Object parameter;
        
        static {
            try {
                INVOKE_METHOD = Executor.class.getMethod("update", MappedStatement.class, Object.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }


        @Override
        public int update(MappedStatement ms, Object parameter) throws SQLException {
            updateCount++;
            mappedStatement = ms;
            this.parameter = parameter;
            return result;
        }

        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException {
            return List.of();
        }

        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
            return List.of();
        }

        @Override
        public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
            return null;
        }

        @Override
        public List<BatchResult> flushStatements() throws SQLException {
            return List.of();
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
