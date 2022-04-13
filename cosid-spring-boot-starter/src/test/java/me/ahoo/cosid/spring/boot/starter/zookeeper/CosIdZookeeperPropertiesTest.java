package me.ahoo.cosid.spring.boot.starter.zookeeper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * CosIdZookeeperPropertiesTest .
 *
 * @author ahoo wang
 */
class CosIdZookeeperPropertiesTest {
    
    @Test
    void isEnabled() {
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        Assertions.assertTrue(properties.isEnabled());
    }
    
    @Test
    void setEnabled() {
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        properties.setEnabled(false);
        Assertions.assertFalse(properties.isEnabled());
    }
    
    @Test
    void getConnectString() {
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        Assertions.assertEquals("localhost:2181", properties.getConnectString());
    }
    
    @Test
    void setConnectString() {
        String connectString = "localhost:2182";
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        properties.setConnectString(connectString);
        Assertions.assertEquals(connectString, properties.getConnectString());
    }
    
    @Test
    void getRetry() {
        int baseSleepTimeMs = 100;
        int maxRetries = 5;
        int maxSleepMs = 500;
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        Assertions.assertNotNull(properties.getRetry());
        Assertions.assertEquals(baseSleepTimeMs, properties.getRetry().getBaseSleepTimeMs());
        Assertions.assertEquals(maxRetries, properties.getRetry().getMaxRetries());
        Assertions.assertEquals(maxSleepMs, properties.getRetry().getMaxSleepMs());
    }
    
    @Test
    void setRetry() {
        int baseSleepTimeMs = 200;
        int maxRetries = 10;
        int maxSleepMs = 1000;
        CosIdZookeeperProperties.Retry retry = new CosIdZookeeperProperties.Retry();
        retry.setBaseSleepTimeMs(baseSleepTimeMs);
        retry.setMaxRetries(maxRetries);
        retry.setMaxSleepMs(maxSleepMs);
        
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        properties.setRetry(retry);
        Assertions.assertNotNull(properties.getRetry());
        Assertions.assertEquals(baseSleepTimeMs, properties.getRetry().getBaseSleepTimeMs());
        Assertions.assertEquals(maxRetries, properties.getRetry().getMaxRetries());
        Assertions.assertEquals(maxSleepMs, properties.getRetry().getMaxSleepMs());
    }
    
    @Test
    void getBlockUntilConnectedWait() {
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        Assertions.assertEquals(Duration.ofSeconds(10), properties.getBlockUntilConnectedWait());
    }
    
    @Test
    void setBlockUntilConnectedWait() {
        Duration blockUntilConnectedWait = Duration.ofSeconds(5);
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        properties.setBlockUntilConnectedWait(blockUntilConnectedWait);
        Assertions.assertEquals(blockUntilConnectedWait, properties.getBlockUntilConnectedWait());
    }
    
    @Test
    void getSessionTimeout() {
        Duration sessionTimeout = Duration.ofSeconds(60);
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        Assertions.assertEquals(sessionTimeout, properties.getSessionTimeout());
    }
    
    @Test
    void setSessionTimeout() {
        Duration sessionTimeout = Duration.ofSeconds(10);
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        properties.setSessionTimeout(sessionTimeout);
        Assertions.assertEquals(sessionTimeout, properties.getSessionTimeout());
    }
    
    @Test
    void getConnectionTimeout() {
        Duration connectionTimeout = Duration.ofSeconds(15);
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        Assertions.assertEquals(connectionTimeout, properties.getConnectionTimeout());
    }
    
    @Test
    void setConnectionTimeout() {
        Duration connectionTimeout = Duration.ofSeconds(10);
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();
        properties.setConnectionTimeout(connectionTimeout);
        Assertions.assertEquals(connectionTimeout, properties.getConnectionTimeout());
    }
}
