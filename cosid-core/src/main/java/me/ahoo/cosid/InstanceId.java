package me.ahoo.cosid;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
public interface InstanceId {
    InstanceId NONE = new DefaultInstanceId( "0.0.0.0", 0);

    String getInstanceId();

    class DefaultInstanceId implements InstanceId {

        private final String host;
        private final int port;
        private final String instanceId;

        public DefaultInstanceId( String host, int port) {
            this.host = host;
            this.port = port;
            this.instanceId = String.format("%s:%s", host, port);
        }

        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public String toString() {
            return instanceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DefaultInstanceId)) return false;
            DefaultInstanceId that = (DefaultInstanceId) o;
            return Objects.equal(getInstanceId(), that.getInstanceId());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getInstanceId());
        }
    }
}
