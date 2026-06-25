package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributor;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdZookeeperSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdZookeeperSegmentAutoConfiguration.class))
        .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
        .withBean(RetryPolicy.class, () -> mock(RetryPolicy.class));

    @Test
    void createsZookeeperSegmentFactoryWithoutConnectingToZookeeper() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context)
                .hasSingleBean(CosIdZookeeperSegmentAutoConfiguration.class)
                .hasSingleBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void backsOffWhenUserProvidesSegmentDistributorFactory() {
        IdSegmentDistributorFactory userFactory = definition -> mock(me.ahoo.cosid.segment.IdSegmentDistributor.class);

        this.contextRunner
            .withBean(IdSegmentDistributorFactory.class, () -> userFactory)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context.getBean(IdSegmentDistributorFactory.class)).isSameAs(userFactory));
    }

    @Test
    void doesNotCreateFactoryWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperSegmentAutoConfiguration.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateFactoryWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperSegmentAutoConfiguration.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateFactoryWhenTypeIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperSegmentAutoConfiguration.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateFactoryWhenZookeeperDistributorClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(ZookeeperIdSegmentDistributor.class))
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperSegmentAutoConfiguration.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }
}
