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
# id generation and its Cache-Control header.

set -euo pipefail

IMAGE="${1:?usage: smoke-test.sh <image>}"
NETWORK="cosid-smoke-$$"
TOKEN="smoke-token"
PORT="${SMOKE_PORT:-18688}"

cleanup() {
  docker rm -f "$NETWORK-proxy" "$NETWORK-no-token" "$NETWORK-redis" > /dev/null 2>&1 || true
  docker network rm "$NETWORK" > /dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  docker logs "$NETWORK-proxy" 2>&1 | tail -50 >&2 || true
  exit 1
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
for _ in $(seq 1 90); do
  if curl -fsS "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -fsS "http://localhost:$PORT/actuator/health" | grep -q '"status":"UP"' || fail "health is not UP"

status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/ids")
[ "$status" = "401" ] || fail "GET /ids without a token answered $status, expected 401"

headers=$(curl -fsS -D - -o /dev/null -H "Authorization: Bearer $TOKEN" "http://localhost:$PORT/ids") || fail "GET /ids with the token failed"
echo "$headers" | grep -qi '^cache-control: no-store' || fail "GET /ids has no Cache-Control: no-store"

status=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $TOKEN" "http://localhost:$PORT/segments/smoke/order?step=10")
[ "$status" = "200" ] || fail "PATCH /segments answered $status, expected 200"

echo "Smoke test passed"
