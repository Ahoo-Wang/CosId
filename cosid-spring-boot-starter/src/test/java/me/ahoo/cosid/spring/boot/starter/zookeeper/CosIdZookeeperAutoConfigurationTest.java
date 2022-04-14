package me.ahoo.cosid.spring.boot.starter.zookeeper;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdZookeeperAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdZookeeperAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @SneakyThrows
    @Test
    void contextLoads() {
        try (TestingServer testingServer = new TestingServer()) {
            testingServer.start();
            this.contextRunner
                .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
                .withPropertyValues(CosIdZookeeperProperties.PREFIX + ".connect-string=" + testingServer.getConnectString())
                .withUserConfiguration(CosIdZookeeperAutoConfiguration.class)
                .run(context -> {
                    assertThat(context)
                        .hasSingleBean(CosIdZookeeperAutoConfiguration.class)
                        .hasSingleBean(CosIdZookeeperProperties.class)
                        .hasSingleBean(RetryPolicy.class)
                        .hasSingleBean(CuratorFramework.class)
                    ;
                });
        }
    }
}
