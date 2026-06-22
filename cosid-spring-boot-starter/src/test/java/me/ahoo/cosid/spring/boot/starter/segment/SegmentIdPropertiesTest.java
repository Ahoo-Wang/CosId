package me.ahoo.cosid.spring.boot.starter.segment;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;
import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

class SegmentIdPropertiesTest {

    @Test
    void defaultsKeepSegmentOptInWithEnabledShareAndRedisDistributor() {
        SegmentIdProperties properties = new SegmentIdProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMode()).isEqualTo(SegmentIdProperties.Mode.CHAIN);
        assertThat(properties.getTtl()).isEqualTo(TIME_TO_LIVE_FOREVER);
        assertThat(properties.getProvider()).isEmpty();
        assertThat(properties.getShare().isEnabled()).isTrue();
        assertThat(properties.getShare().getOffset()).isEqualTo(IdSegmentDistributor.DEFAULT_OFFSET);
        assertThat(properties.getShare().getStep()).isEqualTo(IdSegmentDistributor.DEFAULT_STEP);
        assertThat(properties.getShare().getConverter().getType()).isEqualTo(IdConverterDefinition.Type.RADIX);
        assertThat(properties.getShare().getGroup().getBy()).isEqualTo(SegmentIdProperties.IdDefinition.GroupBy.NEVER);
        assertThat(properties.getChain().getSafeDistance()).isEqualTo(SegmentChainId.DEFAULT_SAFE_DISTANCE);
        assertThat(properties.getChain().getPrefetchWorker().getPrefetchPeriod())
            .isEqualTo(PrefetchWorkerExecutorService.DEFAULT_PREFETCH_PERIOD);
        assertThat(properties.getChain().getPrefetchWorker().getCorePoolSize())
            .isEqualTo(Runtime.getRuntime().availableProcessors());
        assertThat(properties.getChain().getPrefetchWorker().isShutdownHook()).isTrue();
        assertThat(properties.getDistributor().getType()).isEqualTo(SegmentIdProperties.Distributor.Type.REDIS);
        assertThat(properties.getDistributor().getRedis().getTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getDistributor().getJdbc().getIncrementMaxIdSql())
            .isEqualTo(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL);
        assertThat(properties.getDistributor().getJdbc().getFetchMaxIdSql())
            .isEqualTo(JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
        assertThat(properties.getDistributor().getJdbc().isEnableAutoInitCosidTable()).isFalse();
        assertThat(properties.getDistributor().getJdbc().getInitCosidTableSql())
            .isEqualTo(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL);
        assertThat(properties.getDistributor().getJdbc().isEnableAutoInitIdSegment()).isTrue();
        assertThat(properties.getDistributor().getJdbc().getInitIdSegmentSql())
            .isEqualTo(JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL);
        assertThat(properties.getDistributor().getMongo().getDatabase()).isEqualTo("cosid_db");
    }

    @Test
    void binderMapsDistributorChainShareAndNamedProviderDefinitions() {
        SegmentIdProperties properties = bind(Map.ofEntries(
            Map.entry("cosid.segment.enabled", "true"),
            Map.entry("cosid.segment.mode", "segment"),
            Map.entry("cosid.segment.ttl", "600"),
            Map.entry("cosid.segment.distributor.type", "jdbc"),
            Map.entry("cosid.segment.distributor.redis.timeout", "2s"),
            Map.entry("cosid.segment.distributor.jdbc.increment-max-id-sql", "update cosid set max_id=max_id+?"),
            Map.entry("cosid.segment.distributor.jdbc.fetch-max-id-sql", "select max_id from cosid"),
            Map.entry("cosid.segment.distributor.jdbc.enable-auto-init-cosid-table", "true"),
            Map.entry("cosid.segment.distributor.jdbc.init-cosid-table-sql", "create table cosid"),
            Map.entry("cosid.segment.distributor.jdbc.enable-auto-init-id-segment", "false"),
            Map.entry("cosid.segment.distributor.jdbc.init-id-segment-sql", "insert into cosid"),
            Map.entry("cosid.segment.distributor.mongo.database", "segment_db"),
            Map.entry("cosid.segment.chain.safe-distance", "12"),
            Map.entry("cosid.segment.chain.prefetch-worker.prefetch-period", "3s"),
            Map.entry("cosid.segment.chain.prefetch-worker.core-pool-size", "2"),
            Map.entry("cosid.segment.chain.prefetch-worker.shutdown-hook", "false"),
            Map.entry("cosid.segment.share.enabled", "false"),
            Map.entry("cosid.segment.provider.order.namespace", "orders"),
            Map.entry("cosid.segment.provider.order.mode", "chain"),
            Map.entry("cosid.segment.provider.order.offset", "10"),
            Map.entry("cosid.segment.provider.order.step", "100"),
            Map.entry("cosid.segment.provider.order.ttl", "30"),
            Map.entry("cosid.segment.provider.order.chain.safe-distance", "5"),
            Map.entry("cosid.segment.provider.order.group.by", "year_month"),
            Map.entry("cosid.segment.provider.order.group.pattern", "yyyyMM"),
            Map.entry("cosid.segment.provider.order.converter.type", "to_string"),
            Map.entry("cosid.segment.provider.order.converter.to-string.pad-start", "true")
        ));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMode()).isEqualTo(SegmentIdProperties.Mode.SEGMENT);
        assertThat(properties.getTtl()).isEqualTo(600);
        assertThat(properties.getDistributor().getType()).isEqualTo(SegmentIdProperties.Distributor.Type.JDBC);
        assertThat(properties.getDistributor().getRedis().getTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getDistributor().getJdbc().getIncrementMaxIdSql()).isEqualTo("update cosid set max_id=max_id+?");
        assertThat(properties.getDistributor().getJdbc().getFetchMaxIdSql()).isEqualTo("select max_id from cosid");
        assertThat(properties.getDistributor().getJdbc().isEnableAutoInitCosidTable()).isTrue();
        assertThat(properties.getDistributor().getJdbc().getInitCosidTableSql()).isEqualTo("create table cosid");
        assertThat(properties.getDistributor().getJdbc().isEnableAutoInitIdSegment()).isFalse();
        assertThat(properties.getDistributor().getJdbc().getInitIdSegmentSql()).isEqualTo("insert into cosid");
        assertThat(properties.getDistributor().getMongo().getDatabase()).isEqualTo("segment_db");
        assertThat(properties.getChain().getSafeDistance()).isEqualTo(12);
        assertThat(properties.getChain().getPrefetchWorker().getPrefetchPeriod()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getChain().getPrefetchWorker().getCorePoolSize()).isEqualTo(2);
        assertThat(properties.getChain().getPrefetchWorker().isShutdownHook()).isFalse();
        assertThat(properties.getShare().isEnabled()).isFalse();

        SegmentIdProperties.IdDefinition order = properties.getProvider().get("order");
        assertThat(order.getNamespace()).isEqualTo("orders");
        assertThat(order.getMode()).isEqualTo(SegmentIdProperties.Mode.CHAIN);
        assertThat(order.getOffset()).isEqualTo(10);
        assertThat(order.getStep()).isEqualTo(100);
        assertThat(order.getTtl()).isEqualTo(30);
        assertThat(order.getChain().getSafeDistance()).isEqualTo(5);
        assertThat(order.getGroup().getBy()).isEqualTo(SegmentIdProperties.IdDefinition.GroupBy.YEAR_MONTH);
        assertThat(order.getGroup().getPattern()).isEqualTo("yyyyMM");
        assertThat(order.getConverter().getType()).isEqualTo(IdConverterDefinition.Type.TO_STRING);
        assertThat(order.getConverter().getToString().isPadStart()).isTrue();
    }

    private static SegmentIdProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
            .bind(SegmentIdProperties.PREFIX, SegmentIdProperties.class)
            .get();
    }
}
