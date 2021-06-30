package me.ahoo.cosid.spring.boot.starter.snowflake;

import com.google.common.base.Preconditions;
import me.ahoo.cosid.redis.RedisMachineIdDistributor;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSnowflakeEnabled
@ConditionalOnClass(RedisMachineIdDistributor.class)
public class RedisMachineIdDistributorAutoConfiguration {

    private final SnowflakeIdProperties snowflakeIdProperties;

    public RedisMachineIdDistributorAutoConfiguration(SnowflakeIdProperties snowflakeIdProperties) {
        this.snowflakeIdProperties = snowflakeIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(value = SnowflakeIdProperties.Machine.Distributor.TYPE, havingValue = "redis")
    public RedisMachineIdDistributor redisMachineIdDistributor(@Nullable RedisConnectionFactory redisConnectionFactory, MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        Preconditions.checkNotNull(redisConnectionFactory, "redisConnectionFactory can not be null.");
        Duration timeout = snowflakeIdProperties.getMachine().getDistributor().getRedis().getTimeout();
        return new RedisMachineIdDistributor(timeout, redisConnectionFactory.getShareAsyncCommands(), localMachineState, clockBackwardsSynchronizer);
    }

}
