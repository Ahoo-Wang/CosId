# gradle cosid-proxy-server:installDist
# docker buildx build --push --platform linux/amd64,linux/arm64 -t ahoowang/cosid-proxy:TAG .
# docker run --name cosid-proxy -d -p 8688:8688 --link redis -e SPRING_DATA_REDIS_URL=redis://redis:6379 -e COSID_PROXY_TOKEN=change-me ahoowang/cosid-proxy:main

ARG APP_NAME=cosid-proxy-server
ARG WORK_HOME=/opt/${APP_NAME}

FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:25-ubuntu AS base

FROM base as build
ARG WORK_HOME
ARG APP_NAME

WORKDIR ${WORK_HOME}
COPY build/install/${APP_NAME} .

FROM base as run
ARG WORK_HOME

LABEL maintainer="ahoowang@qq.com"

RUN groupadd -r appgroup && useradd -r -g appgroup appuser \
    && (command -v curl > /dev/null || (apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*))

COPY --from=build --chown=appuser:appgroup ${WORK_HOME} ${WORK_HOME}

WORKDIR ${WORK_HOME}
EXPOSE 8688
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8688/actuator/health/liveness || exit 1

USER appuser
STOPSIGNAL SIGTERM

ENTRYPOINT ["bin/cosid-proxy-server"]
