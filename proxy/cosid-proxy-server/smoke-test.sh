#!/usr/bin/env bash
#
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
#

# Smoke test of the cosid-proxy image: ./smoke-test.sh <image>
# Starts Redis and the image on a private network, then checks startup without a token, health, authentication,
# id generation and its Cache-Control header; then the same with PostgreSQL as the store instead of Redis.

set -euo pipefail

IMAGE="${1:?usage: smoke-test.sh <image>}"
NETWORK="cosid-smoke-$$"
TOKEN="smoke-token"
PORT="${SMOKE_PORT:-18688}"
PG_PORT="${SMOKE_PG_PORT:-18690}"

cleanup() {
  docker rm -f "$NETWORK-proxy" "$NETWORK-no-token" "$NETWORK-redis" "$NETWORK-pg" "$NETWORK-proxy-pg" > /dev/null 2>&1 || true
  docker network rm "$NETWORK" > /dev/null 2>&1 || true
}
trap cleanup EXIT

PROXY="$NETWORK-proxy"
fail() {
  echo "FAIL: $*" >&2
  docker logs "$PROXY" 2>&1 | tail -50 >&2 || true
  exit 1
}

# check_server <port>: health UP, 401 without the token, ids with Cache-Control: no-store, and a segment.
check_server() {
  local port=$1 status headers
  for _ in $(seq 1 90); do
    if curl -fsS "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  curl -fsS "http://localhost:$port/actuator/health" | grep -q '"status":"UP"' || fail "health is not UP: $(curl -s "http://localhost:$port/actuator/health")"

  status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/ids")
  [ "$status" = "401" ] || fail "GET /ids without a token answered $status, expected 401"

  headers=$(curl -fsS -D - -o /dev/null -H "Authorization: Bearer $TOKEN" "http://localhost:$port/ids") || fail "GET /ids with the token failed"
  echo "$headers" | grep -qi '^cache-control: no-store' || fail "GET /ids has no Cache-Control: no-store"

  status=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $TOKEN" "http://localhost:$port/segments/smoke/order?step=10")
  [ "$status" = "200" ] || fail "PATCH /segments answered $status, expected 200"
}

docker network create "$NETWORK" > /dev/null
docker run -d --name "$NETWORK-redis" --network "$NETWORK" redis:7.4-alpine > /dev/null

echo "Without a token the server refuses to start"
if docker run --name "$NETWORK-no-token" --network "$NETWORK" -e SPRING_DATA_REDIS_URL="redis://$NETWORK-redis:6379" "$IMAGE" > /dev/null 2>&1; then
  fail "the server started without a token"
fi

echo "With a token the server becomes healthy"
docker run -d --name "$NETWORK-proxy" --network "$NETWORK" -p "$PORT:8688" \
  -e SPRING_DATA_REDIS_URL="redis://$NETWORK-redis:6379" -e COSID_PROXY_TOKEN="$TOKEN" "$IMAGE" > /dev/null
check_server "$PORT"

echo "With PostgreSQL instead of Redis the server becomes healthy"
docker run -d --name "$NETWORK-pg" --network "$NETWORK" -e POSTGRES_DB=cosid_db -e POSTGRES_PASSWORD=cosid postgres:16-alpine > /dev/null
for _ in $(seq 1 60); do
  if docker exec "$NETWORK-pg" pg_isready -U postgres -d cosid_db > /dev/null 2>&1; then
    break
  fi
  sleep 1
done
# The PostgreSQL statements of the JDBC backend guide, with the tables created at startup.
PG_CONFIG=$(cat <<'JSON'
{"cosid":{"machine":{"distributor":{"type":"jdbc","jdbc":{
  "enable-auto-init-cosid-machine-table":true,
  "init-cos-id-machine-table-sql":"create table if not exists cosid_machine (name varchar(100) not null, namespace varchar(100) not null, machine_id integer not null default 0, last_timestamp bigint not null default 0, instance_id varchar(100) not null default '', distribute_time bigint not null default 0, revert_time bigint not null default 0, stable smallint not null default 0, renew_time bigint not null default 0, watermark bigint not null default 0, lease_version bigint not null default 0, constraint cosid_machine_pk primary key (name))",
  "init-namespace-idx-sql":"create index if not exists idx_namespace on cosid_machine (namespace)",
  "init-instance-id-idx-sql":"create index if not exists idx_instance_id on cosid_machine (instance_id)"}}},
 "segment":{"distributor":{"type":"jdbc","jdbc":{
  "enable-auto-init-cosid-table":true,
  "init-cosid-table-sql":"create table if not exists cosid (name varchar(100) not null, last_max_id bigint not null default 0, last_fetch_time bigint not null default 0, constraint cosid_pk primary key (name))",
  "init-id-segment-sql":"insert into cosid (name, last_max_id, last_fetch_time) values (?, ?, cast(extract(epoch from now()) as bigint))",
  "increment-max-id-sql":"update cosid set last_max_id=(last_max_id + ?),last_fetch_time=cast(extract(epoch from now()) as bigint) where name = ?",
  "fetch-max-id-sql":"select last_max_id from cosid where name = ?"}}}}}
JSON
)
PROXY="$NETWORK-proxy-pg"
docker run -d --name "$PROXY" --network "$NETWORK" -p "$PG_PORT:8688" -e COSID_PROXY_TOKEN="$TOKEN" \
  -e SPRING_AUTOCONFIGURE_EXCLUDE="org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://$NETWORK-pg:5432/cosid_db" -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=cosid \
  -e SPRING_APPLICATION_JSON="$PG_CONFIG" "$IMAGE" > /dev/null
check_server "$PG_PORT"

echo "Smoke test passed"
