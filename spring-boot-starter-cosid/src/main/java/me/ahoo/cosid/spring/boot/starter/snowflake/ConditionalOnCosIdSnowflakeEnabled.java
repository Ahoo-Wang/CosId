package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.spring.boot.starter.EnabledSuffix;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ahoo wang
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(value = ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY, matchIfMissing = true, havingValue = "true")
public @interface ConditionalOnCosIdSnowflakeEnabled {
    String ENABLED_KEY = SnowflakeIdProperties.PREFIX + EnabledSuffix.KEY;
}
