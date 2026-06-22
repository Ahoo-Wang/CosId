package me.ahoo.cosid.spring.boot.starter.zookeeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributorFactory;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdZookeeperAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdZookeeperAutoConfiguration.class));

    @Test
    void createsRetryPolicyFromBoundPropertiesWithoutStartingCuratorWhenUserProvidesCurator() {
        CuratorFramework curator = mock(CuratorFramework.class);

        this.contextRunner
            .withBean(CuratorFramework.class, () -> curator)
            .withPropertyValues(
                "cosid.zookeeper.retry.base-sleep-time-ms=25",
                "cosid.zookeeper.retry.max-retries=3",
                "cosid.zookeeper.retry.max-sleep-ms=250"
            )
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdZookeeperProperties.class)
                    .hasSingleBean(RetryPolicy.class)
                    .hasSingleBean(CuratorFramework.class);
                assertThat(context.getBean(RetryPolicy.class)).isInstanceOf(ExponentialBackoffRetry.class);
                assertThat(context.getBean(CuratorFramework.class)).isSameAs(curator);
            });
    }

    @Test
    void backsOffForUserProvidedRetryPolicy() {
        RetryPolicy retryPolicy = mock(RetryPolicy.class);

        this.contextRunner
            .withBean(RetryPolicy.class, () -> retryPolicy)
            .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
            .run(context -> assertThat(context.getBean(RetryPolicy.class)).isSameAs(retryPolicy));
    }

    @Test
    void doesNotCreateZookeeperInfrastructureWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(RetryPolicy.class)
                .doesNotHaveBean(CuratorFramework.class));
    }

    @Test
    void doesNotCreateZookeeperInfrastructureWhenZookeeperIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.zookeeper.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(RetryPolicy.class)
                .doesNotHaveBean(CuratorFramework.class));
    }

    @Test
    void doesNotCreateZookeeperInfrastructureWhenCosIdZookeeperModuleIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(ZookeeperIdSegmentDistributorFactory.class))
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(RetryPolicy.class)
                .doesNotHaveBean(CuratorFramework.class));
    }
}
