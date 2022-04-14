package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperProperties;

import lombok.SneakyThrows;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdZookeeperSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdZookeeperSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @SneakyThrows
    @Test
    void contextLoads() {
        try (TestingServer testingServer = new TestingServer()) {
            testingServer.start();
            this.contextRunner
                .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
                .withPropertyValues(CosIdZookeeperProperties.PREFIX + ".connect-string=" + testingServer.getConnectString())
                .withUserConfiguration(CosIdZookeeperAutoConfiguration.class, CosIdZookeeperSegmentAutoConfiguration.class)
                .run(context -> {
                    assertThat(context)
                        .hasSingleBean(CosIdZookeeperSegmentAutoConfiguration.class)
                        .hasSingleBean(IdSegmentDistributorFactory.class)
                    ;
                });
        }
    }
}
