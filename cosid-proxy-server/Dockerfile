# gradle cosid-proxy-server:installDist
# docker buildx build --push --platform linux/amd64,linux/arm64 -t ahoowang/cosid-proxy:TAG .
# docker run --name cosid-proxy -d -p 8088:8088 --link redis -e SPRING_REDIS_URL=redis://redis:6379 ahoowang/cosid-proxy:0.0.1

ARG APP_NAME=cosid-proxy-server
ARG WORK_HOME=/opt/${APP_NAME}

FROM openjdk:17-jdk-slim AS base

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
