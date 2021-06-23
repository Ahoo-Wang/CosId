package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.CosId;
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
@ConditionalOnProperty(value = ConditionalOnCosIdEnabled.ENABLED_KEY, matchIfMissing = true)
public @interface ConditionalOnCosIdEnabled {
    String ENABLED_KEY = CosId.COSID_PREFIX + "enabled";
}
