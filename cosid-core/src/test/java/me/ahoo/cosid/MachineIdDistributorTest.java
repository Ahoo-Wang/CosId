package me.ahoo.cosid;

import lombok.var;
import me.ahoo.cosid.k8s.StatefulSetMachineIdDistributor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ahoo wang
 */
class MachineIdDistributorTest {

    @Test
    @SetEnvironmentVariable(
            key = StatefulSetMachineIdDistributor.HOSTNAME_KEY,
            value = "cosid-host-6")
    void distribute() {
        var machineId = StatefulSetMachineIdDistributor.INSTANCE.distribute("", 0, InstanceId.NONE);
        Assertions.assertEquals(6, machineId);
    }
}
