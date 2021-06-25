package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.internal.Exceptions;
import me.ahoo.cosid.snowflake.machine.DefaultInstanceId;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.LocalMachineState;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
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


    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
    }

    @Test
    void distribute_none_local() {
        RedisMachineIdDistributor redisMachineIdDistributor = new RedisMachineIdDistributor(redisConnection.async());
        int machineBit = 1;
        String namespace = UUID.randomUUID().toString();
        InstanceId instanceId = DefaultInstanceId.of("127.0.0.1", 80, false);
        int machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        InstanceId instanceId1 = DefaultInstanceId.of("127.0.0.1", 82, false);
        int machineId1 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId1);

        Throwable machineIdOverflowException = null;
        try {
            InstanceId instanceId2 = DefaultInstanceId.of("127.0.0.1", 83, false);
            int machineId2 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
            Assertions.assertEquals(-1, machineId2);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }

        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        redisMachineIdDistributor.revert(namespace, instanceId);
        InstanceId instanceId3 = DefaultInstanceId.of("127.0.0.1", 84, false);
        int machineId3 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
        Assertions.assertEquals(0, machineId3);
    }

    @Test
    void distribute_none_local_stable() {
        RedisMachineIdDistributor redisMachineIdDistributor = new RedisMachineIdDistributor(redisConnection.async());
        int machineBit = 1;
        String namespace = UUID.randomUUID().toString();
        InstanceId instanceId = DefaultInstanceId.of("127.0.0.1", 80, true);
        int machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        InstanceId instanceId1 = DefaultInstanceId.of("127.0.0.1", 82, true);
        int machineId1 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId1);

        Throwable machineIdOverflowException = null;
        try {
            InstanceId instanceId2 = DefaultInstanceId.of("127.0.0.1", 83, true);
            int machineId2 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
            Assertions.assertEquals(-1, machineId2);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }
        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        redisMachineIdDistributor.revert(namespace, instanceId);
        try {
            InstanceId instanceId3 = DefaultInstanceId.of("127.0.0.1", 84, true);
            int machineId3 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
            Assertions.assertEquals(-1, machineId3);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }
        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
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
