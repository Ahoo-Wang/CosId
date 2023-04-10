package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.converter.Radix62IdConverter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * IdConverterDefinitionTest .
 *
 * @author ahoo wang
 */
class IdConverterDefinitionTest {
    
    @Test
    void getType() {
        IdConverterDefinition definition = new IdConverterDefinition();
        Assertions.assertEquals(IdConverterDefinition.Type.RADIX, definition.getType());
    }
    
    @Test
    void setType() {
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setType(IdConverterDefinition.Type.SNOWFLAKE_FRIENDLY);
        Assertions.assertEquals(IdConverterDefinition.Type.SNOWFLAKE_FRIENDLY, definition.getType());
    }
    
    @Test
    void getPrefix() {
        IdConverterDefinition definition = new IdConverterDefinition();
        Assertions.assertNull(definition.getPrefix());
    }
    
    @Test
    void setPrefix() {
        String prefix = "test";
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setPrefix(prefix);
        Assertions.assertEquals(prefix, definition.getPrefix());
    }
    
    @Test
    void getSuffix() {
        IdConverterDefinition definition = new IdConverterDefinition();
        Assertions.assertNull(definition.getSuffix());
    }
    
    @Test
    void setSuffix() {
        String suffix = "test";
        IdConverterDefinition definition = new IdConverterDefinition();
        definition.setSuffix(suffix);
        Assertions.assertEquals(suffix, definition.getSuffix());
    }
    
    @Test
    void getRadix() {
        IdConverterDefinition definition = new IdConverterDefinition();
        Assertions.assertNotNull(definition.getRadix());
        Assertions.assertTrue(definition.getRadix().isPadStart());
        Assertions.assertEquals(Radix62IdConverter.MAX_CHAR_SIZE, definition.getRadix().getCharSize());
    }
    
    
    @Test
    void setRadix() {
        IdConverterDefinition definition = new IdConverterDefinition();
        IdConverterDefinition.Radix radix = new IdConverterDefinition.Radix();
        radix.setPadStart(false);
        radix.setCharSize(10);
        definition.setRadix(radix);
        Assertions.assertNotNull(definition.getRadix());
        Assertions.assertFalse(definition.getRadix().isPadStart());
        Assertions.assertEquals(10, definition.getRadix().getCharSize());
    }
    
    @Test
    void getToString() {
        IdConverterDefinition definition = new IdConverterDefinition();
        Assertions.assertNull(definition.getToString());
    }
    
    @Test
    void setToString() {
        IdConverterDefinition definition = new IdConverterDefinition();
        IdConverterDefinition.ToString toString = new IdConverterDefinition.ToString();
        toString.setPadStart(false);
        toString.setCharSize(10);
        definition.setToString(toString);
        Assertions.assertNotNull(definition.getToString());
        Assertions.assertFalse(definition.getToString().isPadStart());
        Assertions.assertEquals(10, definition.getToString().getCharSize());
    }
}
