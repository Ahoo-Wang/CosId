package me.ahoo.cosid.spring.boot.starter.segment;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * SegmentIdPropertiesTest .
 *
 * @author ahoo wang
 */
class SegmentIdPropertiesTest {
    
    @Test
    void isEnabled() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertFalse(properties.isEnabled());
    }
    
    @Test
    void setEnabled() {
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setEnabled(true);
        Assertions.assertTrue(properties.isEnabled());
    }
    
    @Test
    void getMode() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertEquals(SegmentIdProperties.Mode.CHAIN, properties.getMode());
    }
    
    @Test
    void setMode() {
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setMode(SegmentIdProperties.Mode.SEGMENT);
        Assertions.assertEquals(SegmentIdProperties.Mode.SEGMENT, properties.getMode());
    }
    
    @Test
    void getTtl() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertEquals(TIME_TO_LIVE_FOREVER, properties.getTtl());
    }
    
    @Test
    void setTtl() {
        long ttl = 10;
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setTtl(ttl);
        Assertions.assertEquals(ttl, properties.getTtl());
    }
    
    @Test
    void getDistributor() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getDistributor());
    }
    
    @Test
    void setDistributor() {
        SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setDistributor(distributor);
        Assertions.assertEquals(distributor, properties.getDistributor());
    }
    
    @Test
    void getChain() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getChain());
    }
    
    @Test
    void setChain() {
        SegmentIdProperties.Chain chain = new SegmentIdProperties.Chain();
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setChain(chain);
        Assertions.assertEquals(chain, properties.getChain());
    }
    
    @Test
    void getShare() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getShare());
    }
    
    @Test
    void setShare() {
        SegmentIdProperties.ShardIdDefinition idDefinition = new SegmentIdProperties.ShardIdDefinition();
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setShare(idDefinition);
        Assertions.assertEquals(idDefinition, properties.getShare());
    }
    
    @Test
    void getProvider() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getProvider());
        Assertions.assertTrue(properties.getProvider().isEmpty());
    }
    
    @Test
    void setProvider() {
        Map<String, SegmentIdProperties.IdDefinition> provider = new HashMap<>();
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setProvider(provider);
        Assertions.assertEquals(provider, properties.getProvider());
    }
    
    public static class ChainTest {
        @Test
        public void getSafeDistance() {
            SegmentIdProperties.Chain chain = new SegmentIdProperties.Chain();
            Assertions.assertEquals(SegmentChainId.DEFAULT_SAFE_DISTANCE, chain.getSafeDistance());
        }
        
        @Test
        public void setSafeDistance() {
            int safeDistance = 1;
            SegmentIdProperties.Chain chain = new SegmentIdProperties.Chain();
            chain.setSafeDistance(safeDistance);
            Assertions.assertEquals(safeDistance, chain.getSafeDistance());
        }
        
        @Test
        public void getPrefetchWorker() {
            SegmentIdProperties.Chain chain = new SegmentIdProperties.Chain();
            Assertions.assertNotNull(chain.getPrefetchWorker());
        }
        
        @Test
        public void setPrefetchWorker() {
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            SegmentIdProperties.Chain chain = new SegmentIdProperties.Chain();
            chain.setPrefetchWorker(prefetchWorker);
            Assertions.assertEquals(prefetchWorker, chain.getPrefetchWorker());
        }
    }
    
    public static class PrefetchWorkerTest {
        @Test
        public void getPrefetchPeriod() {
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            Assertions.assertEquals(PrefetchWorkerExecutorService.DEFAULT_PREFETCH_PERIOD, prefetchWorker.getPrefetchPeriod());
        }
        
        @Test
        public void setPrefetchPeriod() {
            Duration prefetchPeriod = Duration.ZERO;
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            prefetchWorker.setPrefetchPeriod(prefetchPeriod);
            Assertions.assertEquals(prefetchPeriod, prefetchWorker.getPrefetchPeriod());
        }
        
        @Test
        public void getCorePoolSize() {
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            Assertions.assertEquals(Runtime.getRuntime().availableProcessors(), prefetchWorker.getCorePoolSize());
        }
        
        @Test
        public void setCorePoolSize() {
            int corePoolSize = 1;
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            prefetchWorker.setCorePoolSize(corePoolSize);
            Assertions.assertEquals(corePoolSize, prefetchWorker.getCorePoolSize());
        }
        
        @Test
        public void isShutdownHook() {
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            Assertions.assertTrue(prefetchWorker.isShutdownHook());
        }
        
        @Test
        public void setShutdownHook() {
            SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = new SegmentIdProperties.Chain.PrefetchWorker();
            prefetchWorker.setShutdownHook(false);
            Assertions.assertFalse(prefetchWorker.isShutdownHook());
        }
    }
    
    public static class DistributorTest {
        @Test
        public void getType() {
            SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
            Assertions.assertEquals(SegmentIdProperties.Distributor.Type.REDIS, distributor.getType());
        }
        
        @Test
        public void setType() {
            SegmentIdProperties.Distributor.Type type = SegmentIdProperties.Distributor.Type.JDBC;
            SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
            distributor.setType(type);
            Assertions.assertEquals(type, distributor.getType());
        }
        
        @Test
        public void getRedis() {
            SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
            Assertions.assertNotNull(distributor.getRedis());
        }
        
        @Test
        public void setRedis() {
            SegmentIdProperties.Distributor.Redis redis = new SegmentIdProperties.Distributor.Redis();
            SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
            distributor.setRedis(redis);
            Assertions.assertEquals(redis, distributor.getRedis());
        }
        
        @Test
        public void getJdbc() {
            SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
            Assertions.assertNotNull(distributor.getJdbc());
        }
        
        @Test
        public void setJdbc() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            SegmentIdProperties.Distributor distributor = new SegmentIdProperties.Distributor();
            distributor.setJdbc(jdbc);
            Assertions.assertEquals(jdbc, distributor.getJdbc());
        }
    }
    
    public static class RedisTest {
        @Test
        public void getTimeout() {
            SegmentIdProperties.Distributor.Redis redis = new SegmentIdProperties.Distributor.Redis();
            Assertions.assertEquals(Duration.ofSeconds(1), redis.getTimeout());
        }
        
        @Test
        public void setTimeout() {
            Duration timeout = Duration.ZERO;
            SegmentIdProperties.Distributor.Redis redis = new SegmentIdProperties.Distributor.Redis();
            redis.setTimeout(timeout);
            Assertions.assertEquals(timeout, redis.getTimeout());
        }
    }
    
    public static class JdbcTest {
        @Test
        public void getIncrementMaxIdSql() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            Assertions.assertEquals(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, jdbc.getIncrementMaxIdSql());
        }
        
        @Test
        public void setIncrementMaxIdSql() {
            String incrementMaxIdSql = "Great CosId!";
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            jdbc.setIncrementMaxIdSql(incrementMaxIdSql);
            Assertions.assertEquals(incrementMaxIdSql, jdbc.getIncrementMaxIdSql());
        }
        
        @Test
        public void getFetchMaxIdSql() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            Assertions.assertEquals(JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, jdbc.getFetchMaxIdSql());
        }
        
        @Test
        public void setFetchMaxIdSql() {
            String fetchMaxIdSql = "Great CosId!";
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            jdbc.setFetchMaxIdSql(fetchMaxIdSql);
            Assertions.assertEquals(fetchMaxIdSql, jdbc.getFetchMaxIdSql());
        }
        
        @Test
        public void isEnableAutoInitCosidTable() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            Assertions.assertFalse(jdbc.isEnableAutoInitCosidTable());
        }
        
        @Test
        public void setEnableAutoInitCosidTable() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            jdbc.setEnableAutoInitCosidTable(true);
            Assertions.assertTrue(jdbc.isEnableAutoInitCosidTable());
        }
        
        @Test
        public void getInitCosidTableSql() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            Assertions.assertEquals(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, jdbc.getInitCosidTableSql());
        }
        
        @Test
        public void setInitCosidTableSql() {
            String initCosidTableSql = "Great CosId!";
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            jdbc.setInitCosidTableSql(initCosidTableSql);
            Assertions.assertEquals(initCosidTableSql, jdbc.getInitCosidTableSql());
        }
        
        @Test
        public void isEnableAutoInitIdSegment() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            Assertions.assertTrue(jdbc.isEnableAutoInitIdSegment());
        }
        
        @Test
        public void setEnableAutoInitIdSegment() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            jdbc.setEnableAutoInitCosidTable(false);
            Assertions.assertFalse(jdbc.isEnableAutoInitCosidTable());
        }
        
        @Test
        public void getInitIdSegmentSql() {
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            Assertions.assertEquals(JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL, jdbc.getInitIdSegmentSql());
        }
        
        @Test
        public void setInitIdSegmentSql() {
            String initIdSegmentSql = "Great CosId!";
            SegmentIdProperties.Distributor.Jdbc jdbc = new SegmentIdProperties.Distributor.Jdbc();
            jdbc.setInitIdSegmentSql(initIdSegmentSql);
            Assertions.assertEquals(initIdSegmentSql, jdbc.getInitIdSegmentSql());
        }
    }
    
    public static class IdDefinitionTest {
        @Test
        public void getMode() {
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            Assertions.assertNull(idDefinition.getMode());
        }
        
        @Test
        public void setMode() {
            SegmentIdProperties.Mode mode = SegmentIdProperties.Mode.SEGMENT;
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            idDefinition.setMode(mode);
            Assertions.assertEquals(mode, idDefinition.getMode());
        }
        
        @Test
        public void getOffset() {
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            Assertions.assertEquals(IdSegmentDistributor.DEFAULT_OFFSET, idDefinition.getOffset());
        }
        
        @Test
        public void setOffset() {
            long offset = 100;
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            idDefinition.setOffset(offset);
            Assertions.assertEquals(offset, idDefinition.getOffset());
        }
        
        @Test
        public void getStep() {
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            Assertions.assertEquals(IdSegmentDistributor.DEFAULT_STEP, idDefinition.getStep());
        }
        
        @Test
        public void setStep() {
            long step = 1;
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            idDefinition.setStep(step);
            Assertions.assertEquals(step, idDefinition.getStep());
        }
        
        @Test
        public void getTtl() {
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            Assertions.assertNull(idDefinition.getTtl());
        }
        
        @Test
        public void setTtl() {
            Long ttl = Long.MAX_VALUE;
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            idDefinition.setTtl(ttl);
            Assertions.assertEquals(ttl, idDefinition.getTtl());
        }
        
        @Test
        public void getChain() {
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            Assertions.assertNull(idDefinition.getChain());
        }
        
        @Test
        public void setChain() {
            SegmentIdProperties.Chain chain = new SegmentIdProperties.Chain();
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            idDefinition.setChain(chain);
            Assertions.assertEquals(chain, idDefinition.getChain());
        }
        
        @Test
        public void getConverter() {
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            Assertions.assertNotNull(idDefinition.getConverter());
        }
        
        @Test
        public void setConverter() {
            IdConverterDefinition converter = new IdConverterDefinition();
            SegmentIdProperties.IdDefinition idDefinition = new SegmentIdProperties.IdDefinition();
            idDefinition.setConverter(converter);
            Assertions.assertEquals(converter, idDefinition.getConverter());
        }
    }
}
