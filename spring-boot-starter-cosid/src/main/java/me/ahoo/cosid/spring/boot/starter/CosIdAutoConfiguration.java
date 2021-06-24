package me.ahoo.cosid.spring.boot.starter;

import com.google.common.base.Preconditions;
import me.ahoo.cosid.*;
import me.ahoo.cosid.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.redis.RedisMachineIdDistributor;
import me.ahoo.cosid.snowflake.*;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.core.util.Systems;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.cloud.commons.util.InetUtils;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@EnableConfigurationProperties(CosIdProperties.class)
public class CosIdAutoConfiguration {
    private final CosIdProperties cosIdProperties;

    public CosIdAutoConfiguration(CosIdProperties cosIdProperties) {
        this.cosIdProperties = cosIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public InstanceId instanceId(InetUtils inetUtils) {
        InetUtils.HostInfo hostInfo = inetUtils.findFirstNonLoopbackHostInfo();
        int port = (int) Systems.getCurrentProcessId();

        if (Objects.nonNull(cosIdProperties.getInstanceId()) && cosIdProperties.getInstanceId().getPort() > 0) {
            port = cosIdProperties.getInstanceId().getPort();
        }

        return new InstanceId.DefaultInstanceId(hostInfo.getIpAddress(), port);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = CosIdProperties.StatefulSet.ENABLED_KEY, havingValue = "true")
    public StatefulSetMachineIdDistributor statefulSetMachineIdDistributor() {
        return StatefulSetMachineIdDistributor.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = CosIdProperties.Manual.ENABLED_KEY, havingValue = "true")
    public MachineIdDistributor manualMachineIdDistributor() {
        Integer machineId = cosIdProperties.getManual().getMachineId();
        Preconditions.checkNotNull(machineId, "me.ahoo.cosid.manual.machineId can not be null.");
        return new MachineIdDistributor.ManualMachineIdDistributor(machineId);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(value = CosIdProperties.Redis.ENABLED_KEY, havingValue = "true")
    public RedisMachineIdDistributor redisMachineIdDistributor(RedisConnectionFactory redisConnectionFactory) {
        return new RedisMachineIdDistributor(redisConnectionFactory.getShareAsyncCommands());
    }

    @Bean
    @ConditionalOnBean(value = MachineIdDistributor.class)
    public LifecycleMachineIdDistributor lifecycleMachineIdDistributor(InstanceId instanceId, MachineIdDistributor machineIdDistributor) {
        return new LifecycleMachineIdDistributor(cosIdProperties, instanceId, machineIdDistributor);
    }

    @Primary
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InstanceId.class)
    public MillisecondSnowflakeId shareIdGenerator(MachineIdDistributor machineIdDistributor, InstanceId instanceId) {
        CosIdProperties.Provider shareProvider = cosIdProperties.getShare();
        return createIdGen(machineIdDistributor, instanceId, shareProvider);
    }

    private MillisecondSnowflakeId createIdGen(MachineIdDistributor machineIdDistributor, InstanceId instanceId, CosIdProperties.Provider provider) {
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), provider.getMachineBit(), instanceId);
        return new MillisecondSnowflakeId(provider.getEpoch(), provider.getTimestampBit(), provider.getMachineBit(), provider.getSequenceBit(), machineId);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InstanceId.class)
    public IdGeneratorProvider idGeneratorProvider(@Qualifier("shareIdGenerator") MillisecondSnowflakeId shareIdGenerator
            , MachineIdDistributor machineIdDistributor
            , InstanceId instanceId) {
        IdGeneratorProvider idGeneratorProvider = new DefaultIdGeneratorProvider(shareIdGenerator);
        if (Objects.isNull(cosIdProperties.getProviders()) || cosIdProperties.getProviders().isEmpty()) {
            return idGeneratorProvider;
        }
        cosIdProperties.getProviders().forEach((name, provider) -> {
            IdGenerator idGenerator = createIdGen(machineIdDistributor, instanceId, provider);
            idGeneratorProvider.set(name, idGenerator);
        });
        return idGeneratorProvider;
    }
}
