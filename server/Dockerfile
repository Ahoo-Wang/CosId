# Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#      http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# cosid-server (08-remote.md §4). Build the jar first, then the image:
#   ./gradlew :server:bootJar && docker build -t ahoowang/cosid-server:TAG server
#   (or ./gradlew :server:dockerImage)
# Run it on a backing store, with a token (the server refuses to start without one):
#   docker run -d -p 8688:8688 \
#     -e COSID_STORES_MAIN_TYPE=redis -e COSID_STORES_MAIN_OPTIONS_URI=redis://redis:6379 \
#     -e COSID_SERVER_AUTH_TOKENS_0=change-me-to-a-long-random-token ahoowang/cosid-server:TAG
# The runtime is JDK 25 (D48), the bytecode stays Java 21: from JDK 24 on (JEP 491) a virtual thread no longer pins its
# carrier inside synchronized, and the server runs its requests on virtual threads.
FROM eclipse-temurin:25-jre

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
