#!/bin/bash
# healthz-check.sh — curl healthz endpoint dan log hasilnya sebagai JSON line
# Dipanggil oleh cron job setiap 3 menit
# Log: logs/healthz-monitor.log

LOG_DIR="$(cd "$(dirname "$0")" && pwd)/logs"
LOG_FILE="$LOG_DIR/healthz-monitor.log"
TMP_BODY="/tmp/healthz-body.json"
TMP_HTTP="/tmp/healthz-http.txt"

mkdir -p "$LOG_DIR"

# Jalankan curl: body → file, HTTP code → file
HTTP_CODE=$(curl -s -w '%{http_code}' \
  --request GET \
  --url http://localhost:8081/api/v1/bill/health \
  --header 'X-CLIENT-KEY: KPBW' \
  --header 'X-TIMESTAMP: 2026-08-01T10:30:00+07:00' \
  --header 'X-EXTERNAL-ID: KPBW' \
  --connect-timeout 5 \
  --max-time 10 \
  -o "$TMP_BODY")

BODY=$(cat "$TMP_BODY" 2>/dev/null)

# Parse body as JSON object (bisa berupa JSON atau string kosong)
if [ -z "$BODY" ]; then
  BODY_JSON="\"\""
elif echo "$BODY" | python3 -c "import json,sys; json.load(sys.stdin)" 2>/dev/null; then
  BODY_JSON=$(echo "$BODY" | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin)))")
else
  BODY_JSON=$(echo "$BODY" | python3 -c "import json,sys; print(json.dumps(sys.stdin.read().strip()))")
fi

# Tentukan status berdasarkan HTTP code
if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
  STATUS="success"
else
  STATUS="failure"
fi

# Current time in ISO 8601
NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Construct JSON log entry
JSON_ENTRY="{\"time\":\"$NOW\",\"status\":\"$STATUS\",\"httpCode\":$HTTP_CODE,\"body\":$BODY_JSON}"

# Append to log
echo "$JSON_ENTRY" >> "$LOG_FILE"

# Cleanup
rm -f "$TMP_BODY" "$TMP_HTTP"

# Brief output for display
echo "[$NOW] $STATUS (HTTP $HTTP_CODE)"
