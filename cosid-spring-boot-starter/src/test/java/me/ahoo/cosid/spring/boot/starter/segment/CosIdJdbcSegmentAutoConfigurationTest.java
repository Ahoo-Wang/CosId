package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributorFactory;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

/**
 * CosIdJdbcSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdJdbcSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdJdbcSegmentAutoConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class));
    
    @Test
    void createsJdbcSegmentBeansWithoutConnectingToDatabase() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-table=false")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdJdbcSegmentAutoConfiguration.class)
                    .hasSingleBean(SegmentIdProperties.class)
                    .hasSingleBean(JdbcIdSegmentInitializer.class)
                    .hasSingleBean(IdSegmentDistributorFactory.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesJdbcSegmentBeans() {
        JdbcIdSegmentInitializer initializer = mock(JdbcIdSegmentInitializer.class);
        IdSegmentDistributorFactory factory = definition -> mock(me.ahoo.cosid.segment.IdSegmentDistributor.class);

        this.contextRunner
            .withBean(JdbcIdSegmentInitializer.class, () -> initializer)
            .withBean(IdSegmentDistributorFactory.class, () -> factory)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .hasSingleBean(JdbcIdSegmentInitializer.class)
                .hasSingleBean(IdSegmentDistributorFactory.class)
                .getBean(IdSegmentDistributorFactory.class)
                .isSameAs(factory));
    }

    @Test
    void doesNotCreateJdbcSegmentBeansWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcSegmentAutoConfiguration.class)
                .doesNotHaveBean(JdbcIdSegmentInitializer.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateJdbcSegmentBeansWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcSegmentAutoConfiguration.class)
                .doesNotHaveBean(JdbcIdSegmentInitializer.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateJdbcSegmentBeansWhenTypeIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcSegmentAutoConfiguration.class)
                .doesNotHaveBean(JdbcIdSegmentInitializer.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateJdbcSegmentBeansWhenJdbcSegmentClassesAreMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .withClassLoader(new FilteredClassLoader(
                JdbcIdSegmentInitializer.class,
                JdbcIdSegmentDistributorFactory.class
            ))
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcSegmentAutoConfiguration.class)
                .doesNotHaveBean(JdbcIdSegmentInitializer.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }
}
