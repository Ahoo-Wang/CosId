package me.ahoo.cosid.mybatis;

import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.util.MockIdGenerator;

import lombok.SneakyThrows;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
    
    public static class InvocationTarget {
        
        public static final Method INVOKE_METHOD;
        
        static {
            try {
                INVOKE_METHOD = InvocationTarget.class.getMethod("invoke", MappedStatement.class, Object.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        
        public void invoke(MappedStatement statement, Object entity) {
        }
        
    }
}
