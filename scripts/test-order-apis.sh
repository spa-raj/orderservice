#!/bin/bash
# ==============================================================================
# Order Service API Test Suite
# ==============================================================================
# Tests all order service endpoints and Kafka event flow against local
# Docker Compose deployment.
#
# Prerequisites:
#   - userservice running on port 8081 (docker compose)
#   - productservice running on port 8080 (docker compose)
#   - cartservice running on port 8082 (docker compose)
#   - orderservice running on port 8083 (docker compose)
#   - All services on vibevault-network
#
# Usage:
#   ./test-order-apis.sh
#   TOKEN="xxx" ./test-order-apis.sh    # skip OAuth2 flow
# ==============================================================================

set -euo pipefail

USERSERVICE="http://localhost:8081"
PRODUCTSERVICE="http://localhost:8080"
CARTSERVICE="http://localhost:8082"
ORDERSERVICE="http://localhost:8083"

# Local docker-compose credentials
ADMIN_EMAIL="admin@gmail.com"
ADMIN_PASSWORD="abcd@1234"
CLIENT_ID="vibevault-client"
CLIENT_SECRET="abc@12345"
REDIRECT_URI="https://oauth.pstmn.io/v1/callback"
SCOPES="openid+profile+email+read+write"

PASS=0
FAIL=0
SKIP=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# ============================================================================
# Helpers
# ============================================================================

assert_status() {
    local description="$1"
    local expected="$2"
    local actual="$3"
    local body="${4:-}"

    if [ "$actual" = "$expected" ]; then
        echo -e "  ${GREEN}PASS${NC} [$actual] $description"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} [$actual expected $expected] $description"
        [ -n "$body" ] && echo "       Response: $(echo "$body" | head -c 300)"
        FAIL=$((FAIL + 1))
    fi
}

assert_body_contains() {
    local description="$1"
    local expected_substring="$2"
    local body="$3"

    if echo "$body" | grep -qi "$expected_substring"; then
        echo -e "  ${GREEN}PASS${NC} $description (contains '$expected_substring')"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} $description (expected to contain '$expected_substring')"
        echo "       Response: $(echo "$body" | head -c 300)"
        FAIL=$((FAIL + 1))
    fi
}

request() {
    local method="$1"
    local url="$2"
    local headers="${3:-}"
    local data="${4:-}"

    local curl_args=(-s -w "\n%{http_code}" -X "$method" "$url")
    if [ -n "$headers" ]; then
        while IFS= read -r header; do
            [ -n "$header" ] && curl_args+=(-H "$header")
        done <<< "$headers"
    fi
    if [ -n "$data" ]; then
        curl_args+=(-d "$data")
    fi

    local response
    response=$(curl "${curl_args[@]}")
    BODY=$(echo "$response" | head -n -1)
    STATUS=$(echo "$response" | tail -n 1)
}

section() {
    echo ""
    echo -e "${CYAN}--- $1 ---${NC}"
}

urlencode() {
    python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$1"
}

# ============================================================================
# OAuth2 Token Flow
# ============================================================================

get_oauth2_token() {
    set +e
    local username="$1"
    local password="$2"

    local COOKIE_JAR
    COOKIE_JAR=$(mktemp /tmp/order_test_cookies.XXXXXX)

    local AUTH_URL="${USERSERVICE}/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=${SCOPES}"

    curl -s -c "$COOKIE_JAR" -b "$COOKIE_JAR" -L --max-redirs 1 -o /dev/null "$AUTH_URL"

    local LOGIN_PAGE
    LOGIN_PAGE=$(curl -s -c "$COOKIE_JAR" -b "$COOKIE_JAR" "${USERSERVICE}/login")
    local CSRF
    CSRF=$(echo "$LOGIN_PAGE" | grep -oP 'name="_csrf".*?value="\K[^"]+')

    if [ -z "$CSRF" ]; then
        rm -f "$COOKIE_JAR"
        set -e
        echo ""
        return
    fi

    local ENCODED_PASSWORD
    ENCODED_PASSWORD=$(urlencode "$password")
    curl -s -D- -o /dev/null -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "${USERSERVICE}/login" \
        -d "username=${username}&password=${ENCODED_PASSWORD}&_csrf=${CSRF}" > /dev/null

    local AUTHORIZE_RESPONSE
    AUTHORIZE_RESPONSE=$(curl -s -D- -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
        "${USERSERVICE}/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=${SCOPES}&continue")

    local AUTHORIZE_LOCATION
    AUTHORIZE_LOCATION=$(echo "$AUTHORIZE_RESPONSE" | grep -i "^Location:" | tr -d '\r' || true)

    local AUTH_CODE=""

    if echo "$AUTHORIZE_LOCATION" | grep -q "code="; then
        AUTH_CODE=$(echo "$AUTHORIZE_LOCATION" | grep -oP 'code=\K[^&\s]+' || true)
    else
        local CONSENT_BODY
        CONSENT_BODY=$(echo "$AUTHORIZE_RESPONSE" | sed '1,/^\r$/d')
        local STATE
        STATE=$(echo "$CONSENT_BODY" | grep -oP 'name="state"[^>]*value="\K[^"]+' || true)

        if [ -z "$STATE" ]; then
            rm -f "$COOKIE_JAR"
            set -e
            echo ""
            return
        fi

        local CONSENT_RESPONSE
        CONSENT_RESPONSE=$(curl -s -D- -o /dev/null -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "${USERSERVICE}/oauth2/authorize" \
            -d "client_id=${CLIENT_ID}&state=${STATE}&scope=read&scope=profile&scope=write&scope=email")

        local CONSENT_LOCATION
        CONSENT_LOCATION=$(echo "$CONSENT_RESPONSE" | grep -i "^Location:" | tr -d '\r' || true)

        AUTH_CODE=$(echo "$CONSENT_LOCATION" | grep -oP 'code=\K[^&\s]+' || true)
    fi

    if [ -z "$AUTH_CODE" ]; then
        rm -f "$COOKIE_JAR"
        set -e
        echo ""
        return
    fi

    local TOKEN_RESPONSE
    TOKEN_RESPONSE=$(curl -s -X POST "${USERSERVICE}/oauth2/token" \
        -u "${CLIENT_ID}:${CLIENT_SECRET}" \
        -d "grant_type=authorization_code" \
        -d "code=${AUTH_CODE}" \
        -d "redirect_uri=${REDIRECT_URI}")

    local TOKEN
    TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null || echo "")

    rm -f "$COOKIE_JAR"
    set -e
    echo "$TOKEN"
}

# ============================================================================
# Test Suite
# ============================================================================

echo "=============================================="
echo "  Order Service API Test Suite"
echo "=============================================="

# --------------------------------------------------
section "1. Health Checks"
# --------------------------------------------------

request GET "$USERSERVICE/actuator/health"
assert_status "userservice health" "200" "$STATUS"

request GET "$PRODUCTSERVICE/actuator/health"
assert_status "productservice health" "200" "$STATUS"

request GET "$CARTSERVICE/actuator/health"
assert_status "cartservice health" "200" "$STATUS"

request GET "$ORDERSERVICE/actuator/health"
assert_status "orderservice health" "200" "$STATUS"

# --------------------------------------------------
section "2. OAuth2 Token"
# --------------------------------------------------

if [ -n "${TOKEN:-}" ]; then
    echo -e "  ${GREEN}PASS${NC} Using provided TOKEN"
    PASS=$((PASS + 1))
else
    echo "  Obtaining admin OAuth2 token..."
    TOKEN=$(get_oauth2_token "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
fi

if [[ "$TOKEN" =~ ^eyJ.*\..*\..*$ ]]; then
    echo -e "  ${GREEN}PASS${NC} Admin OAuth2 token obtained"
    PASS=$((PASS + 1))
    AUTH_HEADERS="$(printf 'Authorization: Bearer %s\nContent-Type: application/json' "$TOKEN")"
    AUTH_ONLY="Authorization: Bearer $TOKEN"
else
    echo -e "  ${RED}FAIL${NC} Could not obtain OAuth2 token"
    FAIL=$((FAIL + 1))
    echo ""
    echo "=============================================="
    printf "  Results: ${GREEN}%d passed${NC}, ${RED}%d failed${NC}, ${YELLOW}%d skipped${NC}\n" "$PASS" "$FAIL" "$SKIP"
    echo "=============================================="
    exit 1
fi

# --------------------------------------------------
section "3. Orders Before Checkout (should be empty)"
# --------------------------------------------------

request GET "$ORDERSERVICE/orders" "$AUTH_ONLY"
assert_status "GET /orders (empty)" "200" "$STATUS"
assert_body_contains "Response has content array" "content" "$BODY"

TOTAL_ORDERS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalElements', -1))" 2>/dev/null || echo "-1")
echo -e "  ${CYAN}Existing orders: ${TOTAL_ORDERS}${NC}"

# --------------------------------------------------
section "4. Setup: Create product + Add to cart + Checkout"
# --------------------------------------------------

# Create a test product
TIMESTAMP=$(date +%s)
PRODUCT_NAME="OrderTest-Product-${TIMESTAMP}"

request POST "$PRODUCTSERVICE/categories" "$AUTH_HEADERS" '{"name":"Electronics","description":"Electronic devices"}'
if [ "$STATUS" = "200" ] || [ "$STATUS" = "409" ]; then
    echo -e "  ${GREEN}OK${NC} Category 'Electronics' ready"
fi

request POST "$PRODUCTSERVICE/products" "$AUTH_HEADERS" \
    "{\"name\":\"${PRODUCT_NAME}\",\"description\":\"Test product for orders\",\"price\":1499.99,\"currency\":\"INR\",\"categoryName\":\"Electronics\"}"
assert_status "POST /products (create test product)" "200" "$STATUS"
PRODUCT_ID=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null || echo "")

if [ -z "$PRODUCT_ID" ]; then
    echo -e "  ${RED}FAIL${NC} Could not create test product"
    exit 1
fi
echo -e "  ${CYAN}Product ID: ${PRODUCT_ID}${NC}"

# Clear cart first
curl -s -X DELETE "$CARTSERVICE/cart" -H "$AUTH_ONLY" > /dev/null 2>&1
echo -e "  ${GREEN}OK${NC} Cart cleared (clean slate)"

# Add item to cart
request POST "$CARTSERVICE/cart/items" "$AUTH_HEADERS" \
    "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":2}"
assert_status "POST /cart/items (add product)" "201" "$STATUS"

# Checkout cart — this triggers CHECKOUT_INITIATED → orderservice creates order
request POST "$CARTSERVICE/cart/checkout" "$AUTH_ONLY"
assert_status "POST /cart/checkout (trigger order creation)" "200" "$STATUS"

# Wait for Kafka consumer to process the event
echo -e "  ${CYAN}Waiting for order creation via Kafka...${NC}"
sleep 5

# --------------------------------------------------
section "5. GET /orders (should have new order)"
# --------------------------------------------------

request GET "$ORDERSERVICE/orders" "$AUTH_ONLY"
assert_status "GET /orders (after checkout)" "200" "$STATUS"

ORDER_COUNT=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalElements', 0))" 2>/dev/null || echo "0")
if [ "$ORDER_COUNT" -ge 1 ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} Order created (${ORDER_COUNT} total orders)"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} Expected at least 1 order, got ${ORDER_COUNT}"
    FAIL=$((FAIL + 1))
fi

# Extract the first order ID
ORDER_ID=$(echo "$BODY" | python3 -c "
import sys,json
data = json.load(sys.stdin)
orders = data.get('content', [])
if orders:
    print(orders[0]['orderId'])
else:
    print('')
" 2>/dev/null || echo "")

if [ -n "$ORDER_ID" ]; then
    echo -e "  ${CYAN}Order ID: ${ORDER_ID}${NC}"
else
    echo -e "  ${RED}FAIL${NC} Could not extract order ID"
    FAIL=$((FAIL + 1))
fi

# Verify order has correct status
ORDER_STATUS=$(echo "$BODY" | python3 -c "
import sys,json
data = json.load(sys.stdin)
orders = data.get('content', [])
if orders:
    print(orders[0]['status'])
else:
    print('')
" 2>/dev/null || echo "")
if [ "$ORDER_STATUS" = "PENDING" ]; then
    echo -e "  ${GREEN}PASS${NC} Order status is PENDING"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} Expected PENDING status, got ${ORDER_STATUS}"
    FAIL=$((FAIL + 1))
fi

# Verify order has items
ORDER_ITEMS_COUNT=$(echo "$BODY" | python3 -c "
import sys,json
data = json.load(sys.stdin)
orders = data.get('content', [])
if orders:
    print(len(orders[0].get('items', [])))
else:
    print(0)
" 2>/dev/null || echo "0")
if [ "$ORDER_ITEMS_COUNT" -ge 1 ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} Order has ${ORDER_ITEMS_COUNT} item(s)"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} Expected at least 1 item, got ${ORDER_ITEMS_COUNT}"
    FAIL=$((FAIL + 1))
fi

# Verify total amount
ORDER_TOTAL=$(echo "$BODY" | python3 -c "
import sys,json
data = json.load(sys.stdin)
orders = data.get('content', [])
if orders:
    print(orders[0].get('totalAmount', 0))
else:
    print(0)
" 2>/dev/null || echo "0")
echo -e "  ${CYAN}Order total: ${ORDER_TOTAL}${NC}"

# --------------------------------------------------
section "6. GET /orders/{id} (order details)"
# --------------------------------------------------

if [ -n "$ORDER_ID" ]; then
    request GET "$ORDERSERVICE/orders/${ORDER_ID}" "$AUTH_ONLY"
    assert_status "GET /orders/{id} (order details)" "200" "$STATUS"
    assert_body_contains "Order has orderId" "$ORDER_ID" "$BODY"
    assert_body_contains "Order has PENDING status" "PENDING" "$BODY"
    assert_body_contains "Order has items" "items" "$BODY"
    assert_body_contains "Order has product name" "$PRODUCT_NAME" "$BODY"
else
    echo -e "  ${YELLOW}SKIP${NC} No order ID available"
    SKIP=$((SKIP + 4))
fi

# --------------------------------------------------
section "7. GET /orders/{id} (non-existent order)"
# --------------------------------------------------

FAKE_ORDER_ID="00000000-0000-0000-0000-000000000000"
request GET "$ORDERSERVICE/orders/${FAKE_ORDER_ID}" "$AUTH_ONLY"
assert_status "GET /orders/{id} (non-existent → 404)" "404" "$STATUS"
assert_body_contains "Error has ORDER_NOT_FOUND code" "ORDER_NOT_FOUND" "$BODY"

# --------------------------------------------------
section "8. GET /orders/{id} (malformed UUID)"
# --------------------------------------------------

request GET "$ORDERSERVICE/orders/not-a-uuid" "$AUTH_ONLY"
assert_status "GET /orders/not-a-uuid (malformed → 400)" "400" "$STATUS"

# --------------------------------------------------
section "9. Unauthenticated Request"
# --------------------------------------------------

request GET "$ORDERSERVICE/orders"
assert_status "GET /orders (no token → 401)" "401" "$STATUS"

# --------------------------------------------------
section "10. Kafka Event Verification"
# --------------------------------------------------

KAFKA_CONTAINER="cartservice-kafka"
ORDER_TOPIC="order-events"
KAFKA_BOOTSTRAP="localhost:9092"

if docker ps --format '{{.Names}}' | grep -q "$KAFKA_CONTAINER"; then
    echo -e "  ${GREEN}OK${NC} Kafka container running"

    # Consume order-events topic
    ORDER_EVENTS=$(docker exec "$KAFKA_CONTAINER" kafka-console-consumer \
        --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --topic "$ORDER_TOPIC" \
        --from-beginning \
        --timeout-ms 5000 2>/dev/null || echo "")

    EVENT_COUNT=$(echo "$ORDER_EVENTS" | grep -c "eventType" || echo "0")
    echo -e "  ${CYAN}Order events in topic: ${EVENT_COUNT}${NC}"

    if [ "$EVENT_COUNT" -ge 1 ] 2>/dev/null; then
        echo -e "  ${GREEN}PASS${NC} Order events produced (${EVENT_COUNT} events)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} Expected at least 1 order event, got ${EVENT_COUNT}"
        FAIL=$((FAIL + 1))
    fi

    # Verify ORDER_CREATED event
    if echo "$ORDER_EVENTS" | grep -q "ORDER_CREATED"; then
        echo -e "  ${GREEN}PASS${NC} ORDER_CREATED event found"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} ORDER_CREATED event not found"
        FAIL=$((FAIL + 1))
    fi

    # Verify event contains orderId
    if echo "$ORDER_EVENTS" | grep -q "orderId"; then
        echo -e "  ${GREEN}PASS${NC} Order event contains orderId"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} Order event missing orderId"
        FAIL=$((FAIL + 1))
    fi

    # Verify event contains userId
    if echo "$ORDER_EVENTS" | grep -q "userId"; then
        echo -e "  ${GREEN}PASS${NC} Order event contains userId"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} Order event missing userId"
        FAIL=$((FAIL + 1))
    fi
else
    echo -e "  ${YELLOW}SKIP${NC} Kafka container not running — skipping event tests"
    SKIP=$((SKIP + 4))
fi

# --------------------------------------------------
section "11. Idempotency — Duplicate Checkout"
# --------------------------------------------------

# Add item and checkout again
request POST "$CARTSERVICE/cart/items" "$AUTH_HEADERS" \
    "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":1}"
request POST "$CARTSERVICE/cart/checkout" "$AUTH_ONLY"
assert_status "POST /cart/checkout (second checkout)" "200" "$STATUS"

echo -e "  ${CYAN}Waiting for Kafka consumer...${NC}"
sleep 5

# Count orders — should have 2 (different cart events = different orders)
request GET "$ORDERSERVICE/orders" "$AUTH_ONLY"
NEW_ORDER_COUNT=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalElements', 0))" 2>/dev/null || echo "0")
if [ "$NEW_ORDER_COUNT" -ge 2 ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} Second checkout created new order (${NEW_ORDER_COUNT} total)"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} Expected at least 2 orders, got ${NEW_ORDER_COUNT}"
    FAIL=$((FAIL + 1))
fi

# --------------------------------------------------
section "12. Pagination"
# --------------------------------------------------

request GET "$ORDERSERVICE/orders?page=0&size=1" "$AUTH_ONLY"
assert_status "GET /orders?page=0&size=1 (paginated)" "200" "$STATUS"

PAGE_SIZE=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('size', 0))" 2>/dev/null || echo "0")
if [ "$PAGE_SIZE" -eq 1 ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} Page size is 1"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} Expected page size 1, got ${PAGE_SIZE}"
    FAIL=$((FAIL + 1))
fi

PAGE_CONTENT=$(echo "$BODY" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('content', [])))" 2>/dev/null || echo "0")
if [ "$PAGE_CONTENT" -eq 1 ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} Page has 1 order"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}FAIL${NC} Expected 1 order in page, got ${PAGE_CONTENT}"
    FAIL=$((FAIL + 1))
fi

TOTAL_PAGES=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalPages', 0))" 2>/dev/null || echo "0")
if [ "$TOTAL_PAGES" -ge 2 ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} Multiple pages (${TOTAL_PAGES} pages)"
    PASS=$((PASS + 1))
else
    echo -e "  ${YELLOW}WARN${NC} Expected multiple pages, got ${TOTAL_PAGES}"
fi

# --------------------------------------------------
section "13. Order Isolation (multi-user)"
# --------------------------------------------------

# Create a test user
TIMESTAMP2=$(date +%s)
USER_B_EMAIL="order-user-b-${TIMESTAMP2}@test.com"
PHONE_B="91${TIMESTAMP2: -8}"
USER_PASSWORD="Test@1234"

# Ensure CUSTOMER role exists
ADMIN_LOGIN_RESP=$(curl -s -X POST "$USERSERVICE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}")
ADMIN_JJWT=$(echo "$ADMIN_LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null || echo "")

if [ -n "$ADMIN_JJWT" ]; then
    request POST "$USERSERVICE/roles/create" "$(printf 'Authorization: %s\nContent-Type: application/json' "$ADMIN_JJWT")" '{"roleName":"CUSTOMER","description":"Customer role"}'
    echo -e "  ${GREEN}OK${NC} CUSTOMER role ready"
else
    echo -e "  ${YELLOW}WARN${NC} Could not get admin JJWT token"
fi

request POST "$USERSERVICE/auth/signup" "Content-Type: application/json" \
    "{\"email\":\"${USER_B_EMAIL}\",\"password\":\"${USER_PASSWORD}\",\"name\":\"User B\",\"phone\":\"${PHONE_B}\",\"role\":\"CUSTOMER\"}"
if [ "$STATUS" = "201" ] || [ "$STATUS" = "409" ] || [ "$STATUS" = "400" ]; then
    echo -e "  ${GREEN}OK${NC} User B created (${USER_B_EMAIL})"
fi

echo "  Obtaining token for User B..."
TOKEN_B=$(get_oauth2_token "$USER_B_EMAIL" "$USER_PASSWORD")

if [[ "$TOKEN_B" =~ ^eyJ.*\..*\..*$ ]]; then
    echo -e "  ${GREEN}PASS${NC} User B token obtained"
    PASS=$((PASS + 1))

    # User B should NOT see admin's orders
    request GET "$ORDERSERVICE/orders" "Authorization: Bearer $TOKEN_B"
    assert_status "User B: GET /orders" "200" "$STATUS"

    B_ORDER_COUNT=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalElements', -1))" 2>/dev/null || echo "-1")
    if [ "$B_ORDER_COUNT" -eq 0 ] 2>/dev/null; then
        echo -e "  ${GREEN}PASS${NC} User B sees 0 orders (isolated from admin)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} User B sees ${B_ORDER_COUNT} orders — isolation broken!"
        FAIL=$((FAIL + 1))
    fi

    # User B should NOT access admin's order by ID
    if [ -n "$ORDER_ID" ]; then
        request GET "$ORDERSERVICE/orders/${ORDER_ID}" "Authorization: Bearer $TOKEN_B"
        assert_status "User B: GET admin's order (should be 404)" "404" "$STATUS"
    fi
else
    echo -e "  ${YELLOW}SKIP${NC} Could not obtain token for User B"
    SKIP=$((SKIP + 3))
fi

# --------------------------------------------------
echo ""
echo "=============================================="
printf "  Results: ${GREEN}%d passed${NC}, ${RED}%d failed${NC}, ${YELLOW}%d skipped${NC}\n" "$PASS" "$FAIL" "$SKIP"
echo "=============================================="

[ "$FAIL" -eq 0 ]
