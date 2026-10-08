#!/usr/bin/env bash
# End-to-end smoke test against a running stack (docker compose up -d). Creates its own users,
# product and orders, so it works on empty databases (CI) as well as on a dev stack.
#
#   scripts/smoke-test.sh            # reads ADMIN_EMAIL / ADMIN_PASSWORD / RABBITMQ_* from .env
#
# Needs curl and python3. Exits non-zero if any check fails. Stays within the gateway's rate
# limits (sign-up 5/min, login 10/min per IP) until the final check, which trips the login limit.
set -uo pipefail

cd "$(dirname "$0")/.."
env_or_dotenv() { # name default
  local v="${!1:-}"
  if [ -z "$v" ] && [ -f .env ]; then v=$(grep -E "^$1=" .env | tail -1 | cut -d= -f2-); fi
  echo "${v:-$2}"
}
G=$(env_or_dotenv GATEWAY_URL http://localhost:8083)
ORDER_SVC=$(env_or_dotenv ORDER_SERVICE_URL http://localhost:8085)
RMQ=$(env_or_dotenv RABBITMQ_URL http://localhost:15672)
RMQ_USER=$(env_or_dotenv RABBITMQ_USER smartcart)
RMQ_PASS=$(env_or_dotenv RABBITMQ_PASSWORD smartcart)
ADMIN_EMAIL=$(env_or_dotenv ADMIN_EMAIL "")
ADMIN_PASSWORD=$(env_or_dotenv ADMIN_PASSWORD "")
[ -n "$ADMIN_EMAIL" ] && [ -n "$ADMIN_PASSWORD" ] || { echo "ADMIN_EMAIL / ADMIN_PASSWORD not set (env or .env)"; exit 2; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
PASS=0; FAIL=0
RUN=$RANDOM$RANDOM

json() { python3 -c "import json,sys; d=json.load(open('$TMP/body')); print($1)" 2>/dev/null; }
# call METHOD PATH [COOKIE] [JSON] -> sets CODE, body in $TMP/body
call() {
  local args=(-s -o "$TMP/body" -w "%{http_code}" -X "$1" "$G$2")
  [ -n "${3:-}" ] && args+=(-H "Cookie: auth_token=$3")
  [ -n "${4:-}" ] && args+=(-H "Content-Type: application/json" -d "$4")
  CODE=$(curl "${args[@]}")
}
check() { # expected label
  if [ "$CODE" = "$1" ]; then PASS=$((PASS + 1)); printf "PASS %s  %s\n" "$CODE" "$2"
  else FAIL=$((FAIL + 1)); printf "FAIL expected %s got %s  %s  %s\n" "$1" "$CODE" "$2" "$(head -c 200 "$TMP/body")"; fi
}
expect() { # label condition-result(0/1) detail
  if [ "$2" = "1" ]; then PASS=$((PASS + 1)); printf "PASS      %s\n" "$1"
  else FAIL=$((FAIL + 1)); printf "FAIL      %s  (%s)\n" "$1" "$3"; fi
}
login() { # email password -> token on stdout
  curl -s -D - -o /dev/null -X POST "$G/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | tr -d '\r' | sed -n 's/^[Ss]et-[Cc]ookie: auth_token=\([^;]*\).*/\1/p'
}

echo "== waiting for the stack (gateway $G)"
# Probe with logout: routed to user-service like login, but not rate-limited.
for i in $(seq 90); do
  call POST /auth/logout
  [ "$CODE" = "200" ] && break
  sleep 5
done
[ "$CODE" = "200" ] || { echo "gateway/user-service not ready (last HTTP $CODE)"; exit 1; }
# Every service registered and routable (cart, order, payment are behind the gateway too).
for i in $(seq 60); do
  apps=$(curl -s -H "Accept: application/json" "$(env_or_dotenv EUREKA_URL http://localhost:8761)/eureka/apps" \
    | python3 -c "import json,sys; print(len(json.load(sys.stdin)['applications']['application']))" 2>/dev/null)
  [ "${apps:-0}" -ge 6 ] && break
  sleep 5
done
echo "   $apps services registered"; sleep 10   # let gateway/Feign load balancers pick them up

echo "== users and auth"
A_EMAIL="a$RUN@smoke.local"; B_EMAIL="b$RUN@smoke.local"
call POST /auth/register "" "{\"name\":\"Smoke A\",\"email\":\"$A_EMAIL\",\"password\":\"Passw0rd!\",\"role\":\"ADMIN\"}"; check 200 "register user A"
expect "requested ADMIN role is ignored" "$([ "$(json "d['data']['role']")" = "USER" ] && echo 1)" "$(json "d['data']")"
call POST /auth/register "" "{\"name\":\"Smoke B\",\"email\":\"$B_EMAIL\",\"password\":\"Passw0rd!\"}"; check 200 "register user B"
call POST /auth/register "" "{\"name\":\"Dup\",\"email\":\"$A_EMAIL\",\"password\":\"Passw0rd!\"}"; check 409 "duplicate email"
call POST /auth/register "" '{"name":"X","email":"not-an-email"}'; check 400 "invalid registration"
call POST /auth/login "" "{\"email\":\"$A_EMAIL\",\"password\":\"WrongPass1\"}"; check 401 "wrong password"
A=$(login "$A_EMAIL" 'Passw0rd!'); B=$(login "$B_EMAIL" 'Passw0rd!'); ADMIN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
expect "logins issue tokens (A, B, admin)" "$([ -n "$A" ] && [ -n "$B" ] && [ -n "$ADMIN" ] && echo 1)" "A=${#A} B=${#B} admin=${#ADMIN} chars"
call GET /user/me "$A"; check 200 "GET /user/me"

echo "== products"
call POST /products "$ADMIN" '{"title":"Smoke Lamp","price":249.5,"stock":5}'; check 200 "admin creates product"
P=$(json "d['data']['id']")
call POST /products "$A" '{"title":"Nope","price":10,"stock":1}'; check 403 "normal user can't create products"
call POST /products "$ADMIN" '{"stock":-1}'; check 400 "invalid product"
call GET /products/999999999 "$A"; check 404 "unknown product"

echo "== cart"
call POST /carts/add "$A" "{\"productId\":$P,\"quantity\":2}"; check 200 "add to cart"
call POST /carts/add "$A" '{"productId":999999999,"quantity":1}'; check 404 "add unknown product"
call POST /carts/add "$A" '{"quantity":0}'; check 400 "invalid cart item"

echo "== orders"
call POST /orders "$A" '{"items":[]}'; check 400 "empty order"
call POST /orders "$A" "{\"items\":[{\"productId\":$P,\"quantity\":6}]}"; check 409 "more than the stock"
call POST /orders "$A" "{\"items\":[{\"productId\":$P,\"quantity\":2,\"price\":1}],\"status\":\"PAID\"}"; check 200 "place order"
OID=$(json "d['data']['orderId']"); TOTAL=$(json "d['data']['totalAmount']"); STATUS=$(json "d['data']['status']")
expect "server sets price and status (total 499.0, PENDING)" "$([ "$TOTAL" = "499.0" ] && [ "$STATUS" = "PENDING" ] && echo 1)" "total=$TOTAL status=$STATUS"
call GET /products/$P "$A"; expect "stock reserved on order (5 -> 3)" "$([ "$(json "d['data']['stock']")" = "3" ] && echo 1)" "stock=$(json "d['data']['stock']")"
call GET "/orders/by-order-id/$OID" "$B"; check 404 "another user can't see the order"
call PUT "/orders/by-order-id/$OID" "$ADMIN" '{"status":"PAID"}'; check 405 "orders can't be edited directly"
CODE=$(curl -s -o "$TMP/body" -w "%{http_code}" "$ORDER_SVC/orders"); check 401 "order-service port without a token"

echo "== payments"
call POST /payments/create-razorpay-order "$A"; check 400 "missing orderIdRef"
call POST /payments "$A" "{\"orderIdRef\":\"$OID\"}"; check 400 "verification with missing fields"
call POST "/payments/create-razorpay-order?orderIdRef=$OID" "$B"; check 404 "can't start payment for someone else's order"
call GET /payments/999999999 "$A"; check 404 "unknown payment"
call GET /payments "$A"; check 403 "listing all payments is admin-only"

echo "== checkout saga (payment.completed -> order PAID -> stock confirmed, cart cleared)"
EVENT=$(python3 -c "import json,uuid; print(json.dumps({'eventId':str(uuid.uuid4()),'orderId':'$OID','paymentId':None,'status':'PAID','occurredAt':'2026-01-01T10:00:00'}))")
BODY=$(python3 -c "import json,sys; print(json.dumps({'properties':{'content_type':'application/json'},'routing_key':'payment.completed','payload':sys.argv[1],'payload_encoding':'string'}))" "$EVENT")
ROUTED=$(curl -s -u "$RMQ_USER:$RMQ_PASS" "$RMQ/api/exchanges/%2F/payment.events/publish" -H "Content-Type: application/json" -d "$BODY" \
  | python3 -c "import json,sys; print(json.load(sys.stdin).get('routed'))" 2>/dev/null)
expect "payment event published to RabbitMQ" "$([ "$ROUTED" = "True" ] && echo 1)" "routed=$ROUTED"
for i in $(seq 30); do call GET "/orders/by-order-id/$OID" "$A"; [ "$(json "d['data']['status']")" = "PAID" ] && break; sleep 1; done
expect "order becomes PAID" "$([ "$(json "d['data']['status']")" = "PAID" ] && echo 1)" "status=$(json "d['data']['status']")"
sleep 3
call GET /products/$P "$A"; expect "stock stays 3 (reservation confirmed, not taken twice)" "$([ "$(json "d['data']['stock']")" = "3" ] && echo 1)" "stock=$(json "d['data']['stock']")"
call GET /carts "$A"; expect "bought items removed from the cart" "$([ "$(json "len([i for i in d['data']['items'] if i['productId']==$P])")" = "0" ] && echo 1)" "$(head -c 200 "$TMP/body")"
DLQ=$(curl -s -u "$RMQ_USER:$RMQ_PASS" "$RMQ/api/queues" | python3 -c "import json,sys; print(sum(q.get('messages',0) for q in json.load(sys.stdin) if q['name'].endswith('.dlq')))" 2>/dev/null)
expect "nothing dead-lettered" "$([ "${DLQ:-x}" = "0" ] && echo 1)" "dlq messages=$DLQ"

echo "== rate limiting (last: uses up this IP's login allowance for a minute)"
GOT429=0
for i in $(seq 12); do call POST /auth/login "" "{\"email\":\"nobody$i$RUN@smoke.local\",\"password\":\"WrongPass1\"}"; [ "$CODE" = "429" ] && GOT429=1; done
expect "too many logins get 429" "$GOT429" "no 429 in 12 attempts"

echo
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
