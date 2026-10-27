# cosid-server (08-remote.md §4). Build the jar first, then the image:
#   ./gradlew :server:bootJar && docker build -t ahoowang/cosid-server:TAG server
#   (or ./gradlew :server:dockerImage)
# Run it on a backing store, with a token (the server refuses to start without one):
#   docker run -d -p 8688:8688 \
#     -e COSID_STORES_MAIN_TYPE=redis -e COSID_STORES_MAIN_OPTIONS_URI=redis://redis:6379 \
#     -e COSID_SERVER_AUTH_TOKENS_0=change-me ahoowang/cosid-server:TAG
FROM eclipse-temurin:21-jre

LABEL maintainer="ahoowang@qq.com"

RUN groupadd -r cosid && useradd -r -g cosid cosid \
    && (command -v curl > /dev/null || (apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*))

WORKDIR /opt/cosid-server
COPY --chown=cosid:cosid build/libs/cosid-server.jar cosid-server.jar

# The heap follows the container's memory limit; JAVA_OPTS adds to it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8688
# /actuator/health/liveness answers without asking the store.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8688/actuator/health/liveness || exit 1

USER cosid
STOPSIGNAL SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar cosid-server.jar \"$@\"", "--"]
