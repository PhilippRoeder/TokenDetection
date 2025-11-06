#!/usr/bin/env sh
# POSIX-friendly: no arrays, no mapfile. Requires jq and curl.
# Sends all requests in the background (fire-and-forget).

JSON_FILE="${JSON_FILE:-./tokens.json}"
PROXY="${PROXY:-http://127.0.0.1:8080}"
TARGET="${TARGET:-http://localhost:9999/protected}"

# Basic checks
if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required. Install jq and retry." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required." >&2
  exit 1
fi
if [ ! -f "$JSON_FILE" ]; then
  echo "ERROR: JSON file not found at $JSON_FILE" >&2
  exit 1
fi

echo "Proxy : $PROXY"
echo "Target: $TARGET"
echo "JSON  : $JSON_FILE"
echo "Launching background requests..."

count=0

# Fire a curl silently in the background; don't wait.
fire() {
  curl -x "$PROXY" -s -o /dev/null "$@" &
}

# Helper: iterate over a jq array path and apply two sends (header + body)
# $1 = jq path (e.g., .jwt[]?)
# $2 = header name (or empty to skip)
# $3 = header prefix (e.g., "Bearer " or "LtpaToken2=" for Cookie)
# $4 = body field name (or empty to skip)
# $5 = body wrapper prefix (e.g., "" or "LtpaToken2=")
send_tokens() {
  jq -r "$1" "$JSON_FILE" | while IFS= read -r tok; do
    [ -n "$tok" ] || continue

    # Header
    if [ -n "$2" ]; then
      case "$2" in
        "Authorization")
          fire -H "Authorization: ${3}${tok}" "$TARGET"
          ;;
        "Cookie")
          fire -H "Cookie: ${3}${tok}" "$TARGET"
          ;;
        *)
          fire -H "$2: ${3}${tok}" "$TARGET"
          ;;
      esac
      count=$((count+1))
    fi

    # JSON body
    if [ -n "$4" ]; then
      # Escape " inside token for JSON (tokens here don't contain quotes, but be safe)
      esc_tok=$(printf '%s' "$tok" | sed 's/"/\\"/g')
      fire -H "Content-Type: application/json" \
           --data "{\"$4\":\"$5$esc_tok\"}" \
           "$TARGET"
      count=$((count+1))
    fi
  done
}

# JWT: Authorization: Bearer <jwt> + {"token":"<jwt>"}
send_tokens '.jwt[]?' "Authorization" "Bearer " "token" ""

# LTPA2: Cookie: LtpaToken2=<ltpa2> + {"cookie":"LtpaToken2=<ltpa2>"}
send_tokens '.ltpa2[]?' "Cookie" "LtpaToken2=" "cookie" "LtpaToken2="

# PASETO: Authorization: Bearer <paseto> + {"paseto":"<paseto>"}
send_tokens '.paseto[]?' "Authorization" "Bearer " "paseto" ""

echo "Launched $count background requests. Check Burp Proxy history for highlights."
# No 'wait' here — the script exits immediately while curls keep running.
