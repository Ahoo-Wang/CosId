package me.ahoo.cosid.spring.boot.starter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * CosIdPropertiesTest .
 *
 * @author ahoo wang
 */
class CosIdPropertiesTest {
    
    @Test
    void isEnabled() {
        CosIdProperties properties = new CosIdProperties();
        Assertions.assertTrue(properties.isEnabled());
    }
    
    @Test
    void setEnabled() {
        CosIdProperties properties = new CosIdProperties();
        properties.setEnabled(false);
        Assertions.assertFalse(properties.isEnabled());
    }
    
    @Test
    void getNamespace() {
        CosIdProperties properties = new CosIdProperties();
        Assertions.assertEquals(CosIdProperties.DEFAULT_NAMESPACE, properties.getNamespace());
    }
    
    @Test
    void setNamespace() {
        String namespace = "test";
        CosIdProperties properties = new CosIdProperties();
        properties.setNamespace(namespace);
        Assertions.assertEquals(namespace, properties.getNamespace());
    }
}
