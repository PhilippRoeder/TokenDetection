#!/usr/bin/env sh
# POSIX-friendly: jq + curl required. Randomly send N tokenized requests via Burp proxy.
# For each request: (token_kind, where_token_is, which_token_from_json)
set -u

COUNT="${1:-5}"
case "$COUNT" in ''|*[!0-9]*) COUNT=5 ;; esac

JSON_FILE="${JSON_FILE:-./tokens.json}"
PROXY="${PROXY:-http://127.0.0.1:8080}"
TARGET="${TARGET:-http://localhost:9999/protected}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: need '$1' installed." >&2; exit 1; }; }
need jq; need curl; need od

[ -f "$JSON_FILE" ] || { echo "ERROR: JSON not found: $JSON_FILE" >&2; exit 1; }

echo "Proxy : $PROXY"
echo "Target: $TARGET"
echo "JSON  : $JSON_FILE"
echo "Will send $COUNT random request(s)."
echo

# --- Dump each category to a temp file (one token per line), strip CRs & blanks ---
JWT_FILE="$(mktemp)"; LTPA2_FILE="$(mktemp)"; PASETO_FILE="$(mktemp)"; KINDS_FILE="$(mktemp)"
cleanup(){ rm -f "$JWT_FILE" "$LTPA2_FILE" "$PASETO_FILE" "$KINDS_FILE"; }
trap cleanup EXIT INT HUP

jq -r '.jwt[]?    // empty' "$JSON_FILE" | tr -d '\r' | sed '/^$/d' > "$JWT_FILE"
jq -r '.ltpa2[]?  // empty' "$JSON_FILE" | tr -d '\r' | sed '/^$/d' > "$LTPA2_FILE"
jq -r '.paseto[]? // empty' "$JSON_FILE" | tr -d '\r' | sed '/^$/d' > "$PASETO_FILE"

# Count lines (robust)
line_count(){ sed -n '$=' "$1"; }  # prints number of lines (0 if empty)
JWT_N=$(line_count "$JWT_FILE")
LTPA2_N=$(line_count "$LTPA2_FILE")
PASETO_N=$(line_count "$PASETO_FILE")

# Build list of available kinds
[ "$JWT_N"   -gt 0 ] && echo jwt   >> "$KINDS_FILE"
[ "$LTPA2_N" -gt 0 ] && echo ltpa2 >> "$KINDS_FILE"
[ "$PASETO_N" -gt 0 ] && echo paseto >> "$KINDS_FILE"

KINDS_N=$(line_count "$KINDS_FILE")
[ "$KINDS_N" -gt 0 ] || { echo "ERROR: no tokens found in JSON."; exit 1; }

# --- Random helpers (no awk math; pure shell) ---
# rand0: integer in [0, max-1]
rand0() {
  max="$1"
  [ "$max" -gt 0 ] || { echo 0; return; }
  v=$(od -An -N2 -tu2 /dev/urandom 2>/dev/null | tr -d ' ')
  [ -n "$v" ] || v=0
  echo $(( v % max ))
}
# rand1: integer in [1, max]
rand1(){ max="$1"; r=$(rand0 "$max"); echo $(( r + 1 )); }

# Pick nth line (1-based)
line_n(){ file="$1"; n="$2"; sed -n "${n}p" "$file"; }

# Choose random kind and placement valid for that kind
pick_kind(){ idx=$(rand1 "$KINDS_N"); line_n "$KINDS_FILE" "$idx"; }

pick_place(){
  kind="$1"; r=$(rand0 2)
  if [ "$kind" = "jwt" ]; then
    [ "$r" -eq 0 ] && echo "auth" || echo "body"
  elif [ "$kind" = "ltpa2" ]; then
    [ "$r" -eq 0 ] && echo "cookie" || echo "body_cookie"
  else # paseto
    [ "$r" -eq 0 ] && echo "auth" || echo "body_paseto"
  fi
}

# Escape only double quotes for JSON (tokens here don't contain quotes anyway)
json_escape(){ printf '%s' "$1" | sed 's/"/\\"/g'; }

# Fire in background
fire(){ curl -x "$PROXY" -s -o /dev/null "$@" & }

i=1
while [ "$i" -le "$COUNT" ]; do
  kind="$(pick_kind)"
  place="$(pick_place "$kind")"

  case "$kind" in
    jwt)
      idx="$(rand1 "$JWT_N")"
      tok="$(line_n "$JWT_FILE" "$idx")"
      [ -n "$tok" ] || { echo "[$i] skip empty jwt"; i=$((i+1)); continue; }
      if [ "$place" = "auth" ]; then
        fire -H "Authorization: Bearer $tok" "$TARGET"
        where="Authorization header"
      else
        esc="$(json_escape "$tok")"
        fire -H "Content-Type: application/json" --data "{\"token\":\"$esc\"}" "$TARGET"
        where="JSON body (token)"
      fi
      ;;
    ltpa2)
      idx="$(rand1 "$LTPA2_N")"
      tok="$(line_n "$LTPA2_FILE" "$idx")"
      [ -n "$tok" ] || { echo "[$i] skip empty ltpa2"; i=$((i+1)); continue; }
      if [ "$place" = "cookie" ]; then
        fire -H "Cookie: LtpaToken2=$tok" "$TARGET"
        where="Cookie header"
      else
        esc="$(json_escape "$tok")"
        fire -H "Content-Type: application/json" --data "{\"cookie\":\"LtpaToken2=$esc\"}" "$TARGET"
        where="JSON body (cookie-like)"
      fi
      ;;
    paseto)
      idx="$(rand1 "$PASETO_N")"
      tok="$(line_n "$PASETO_FILE" "$idx")"
      [ -n "$tok" ] || { echo "[$i] skip empty paseto"; i=$((i+1)); continue; }
      if [ "$place" = "auth" ]; then
        fire -H "Authorization: Bearer $tok" "$TARGET"
        where="Authorization header"
      else
        esc="$(json_escape "$tok")"
        fire -H "Content-Type: application/json" --data "{\"paseto\":\"$esc\"}" "$TARGET"
        where="JSON body (paseto)"
      fi
      ;;
  esac

  echo "[$i/$COUNT] kind=${kind} | where=${where} | index=${idx}"
  i=$((i+1))
done

echo
echo "Launched $COUNT background request(s). Check Burp Proxy history."
# No 'wait' — curls keep running after exit.
