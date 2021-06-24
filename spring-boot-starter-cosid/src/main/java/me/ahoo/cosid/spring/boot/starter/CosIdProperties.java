package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.CosId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = CosId.COSID)
public class CosIdProperties {
    public final String DEFAULT_NAMESPACE = "{" + CosId.COSID + "}";
    private boolean enabled = true;

    private String namespace = DEFAULT_NAMESPACE;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }


}
