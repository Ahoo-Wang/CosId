# gradle cosid-proxy-server:installDist
# docker buildx build --push --platform linux/amd64,linux/arm64 -t ahoowang/cosid-proxy:TAG .
# docker run --name cosid-proxy -d -p 8688:8688 --link redis -e SPRING_REDIS_URL=redis://redis:6379 ahoowang/cosid-proxy:main --health-cmd "curl --fail --silent localhost:8688/actuator/health | grep UP || exit 1" --health-interval 10s --health-timeout 5s --health-retries 5

ARG APP_NAME=cosid-proxy-server
ARG WORK_HOME=/opt/${APP_NAME}

FROM openjdk:20-jdk-slim AS base

FROM base as build
ARG WORK_HOME
ARG APP_NAME

WORKDIR ${WORK_HOME}
COPY build/install/${APP_NAME} .

FROM base as run
ARG WORK_HOME

LABEL maintainer="ahoowang@qq.com"
COPY --from=build ${WORK_HOME} ${WORK_HOME}

WORKDIR ${WORK_HOME}
EXPOSE 8688

ENTRYPOINT ["bin/cosid-proxy-server"]
