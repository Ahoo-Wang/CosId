package me.ahoo.cosid.spring.boot.starter.redis;

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
@ConditionalOnProperty(value = ConditionalOnCosIdRedisEnabled.ENABLED_KEY, matchIfMissing = false, havingValue = "true")
public @interface ConditionalOnCosIdRedisEnabled {
    String ENABLED_KEY = RedisIdProperties.PREFIX + EnabledSuffix.KEY;
}
