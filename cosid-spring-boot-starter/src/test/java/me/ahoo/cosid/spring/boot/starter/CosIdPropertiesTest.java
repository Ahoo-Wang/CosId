package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

class CosIdPropertiesTest {

    @Test
    void defaultsDescribeEnabledLocalCosId() {
        CosIdProperties properties = new CosIdProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getNamespace()).isEqualTo(CosIdProperties.DEFAULT_NAMESPACE);
        assertThat(properties.getProxy().getHost()).isEqualTo("http://localhost:8688");
    }

    @Test
    void binderMapsCosIdPrefixAndNestedProxyProperties() {
        CosIdProperties properties = bind(Map.of(
            "cosid.enabled", "false",
            "cosid.namespace", "billing",
            "cosid.proxy.host", "https://proxy.example"
        ));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getNamespace()).isEqualTo("billing");
        assertThat(properties.getProxy().getHost()).isEqualTo("https://proxy.example");
    }

    @Test
    void proxySetterIsChainableForProgrammaticCustomization() {
        ProxyProperties proxy = new ProxyProperties().setHost("http://custom-host");
        CosIdProperties properties = new CosIdProperties();

        assertThat(properties.setProxy(proxy)).isSameAs(properties);
        assertThat(properties.getProxy()).isSameAs(proxy);
    }

    private static CosIdProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
            .bind("cosid", CosIdProperties.class)
            .get();
    }
}
