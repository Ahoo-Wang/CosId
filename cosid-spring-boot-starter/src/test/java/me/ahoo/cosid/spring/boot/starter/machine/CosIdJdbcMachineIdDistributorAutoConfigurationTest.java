/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import me.ahoo.cosid.jdbc.JdbcMachineIdDistributor;
import me.ahoo.cosid.jdbc.JdbcMachineIdInitializer;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * CosIdJdbcMachineIdDistributorAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdJdbcMachineIdDistributorAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdJdbcMachineIdDistributorAutoConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withPropertyValues(MachineProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-machine-table=false");
    
    @Test
    void createsJdbcMachineIdDistributorWithoutConnectingToDatabase() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdJdbcMachineIdDistributorAutoConfiguration.class)
                    .hasSingleBean(JdbcMachineIdInitializer.class)
                    .hasSingleBean(JdbcMachineIdDistributor.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesMachineIdDistributor() {
        JdbcMachineIdDistributor userDistributor = mock(JdbcMachineIdDistributor.class);

        this.contextRunner
            .withBean(JdbcMachineIdDistributor.class, () -> userDistributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .hasSingleBean(JdbcMachineIdDistributor.class)
                .getBean(JdbcMachineIdDistributor.class)
                .isSameAs(userDistributor));
    }

    @Test
    void doesNotCreateDistributorWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(JdbcMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(JdbcMachineIdInitializer.class)
                .doesNotHaveBean(JdbcMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenJdbcDistributorClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(JdbcMachineIdDistributor.class))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdJdbcMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(JdbcMachineIdInitializer.class)
                .doesNotHaveBean(JdbcMachineIdDistributor.class));
    }

    @Test
    void initializerDoesNotTouchDataSourceWhenAutoInitIsDisabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdJdbcMachineIdDistributorAutoConfiguration.class))
            .withBean(DataSource.class, () -> dataSource)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .withPropertyValues(MachineProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-machine-table=false")
            .run(context -> {
                assertThat(context).hasSingleBean(JdbcMachineIdInitializer.class);
                verify(dataSource, Mockito.never()).getConnection();
            });
    }

    @Test
    void initializerRunsTableCreationWhenAutoInitIsEnabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        doReturn(connection).when(dataSource).getConnection();
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdJdbcMachineIdDistributorAutoConfiguration.class))
            .withBean(DataSource.class, () -> dataSource)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .withPropertyValues(MachineProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-machine-table=true")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(JdbcMachineIdInitializer.class)
                    .hasSingleBean(JdbcMachineIdDistributor.class);
                verify(dataSource).getConnection();
            });
    }

    @Test
    void backsOffWhenUserProvidesJdbcMachineIdInitializer() {
        JdbcMachineIdInitializer userInitializer = mock(JdbcMachineIdInitializer.class);
        this.contextRunner
            .withBean(JdbcMachineIdInitializer.class, () -> userInitializer)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .hasSingleBean(JdbcMachineIdInitializer.class)
                .getBean(JdbcMachineIdInitializer.class)
                .isSameAs(userInitializer));
    }
}
