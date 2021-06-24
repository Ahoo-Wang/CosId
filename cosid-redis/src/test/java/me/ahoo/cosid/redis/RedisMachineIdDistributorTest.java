package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.internal.Exceptions;
import me.ahoo.cosid.InstanceId;
import me.ahoo.cosid.MachineIdOverflowException;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.UUID;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisMachineIdDistributorTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;
    protected RedisMachineIdDistributor redisMachineIdDistributor;

    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisMachineIdDistributor = new RedisMachineIdDistributor(redisConnection.async());
    }

    @Test
    void distribute() {
        int machineBit = 1;
        String namespace = UUID.randomUUID().toString();
        InstanceId instanceId = new InstanceId.DefaultInstanceId("127.0.0.1", 80);
        int machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        InstanceId instanceId1 = new InstanceId.DefaultInstanceId("127.0.0.1", 82);
        int machineId1 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId1);

        Throwable machineIdOverflowException = null;
        try {
            InstanceId instanceId2 = new InstanceId.DefaultInstanceId("127.0.0.1", 83);
            int machineId2 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
            Assertions.assertEquals(-1, machineId2);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }

        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        redisMachineIdDistributor.revert(namespace, instanceId);
        InstanceId instanceId3 = new InstanceId.DefaultInstanceId("127.0.0.1", 84);
        int machineId3 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
        Assertions.assertEquals(0, machineId3);

    }

    @AfterAll
    private void destroyRedis() {
        System.out.println("--- destroyRedis ---");

        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
