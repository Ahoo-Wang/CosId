package me.ahoo.cosid;

import lombok.var;
import me.ahoo.cosid.snowflake.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

/**
 * @author ahoo wang
 */
class MachineIdDistributorTest {

    @Test
    @SetEnvironmentVariable(
            key = StatefulSetMachineIdDistributor.HOSTNAME_KEY,
            value = "cosid-host-6")
    void distribute() {
        var machineId = StatefulSetMachineIdDistributor.INSTANCE.distribute("k8s", 0, InstanceId.NONE);
        Assertions.assertEquals(6, machineId);
    }
}
