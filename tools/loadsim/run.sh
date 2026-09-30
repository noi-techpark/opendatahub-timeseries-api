#!/usr/bin/env bash
# Replays a fixed, production-derived request pattern against a ninja-api instance, so repeated
# runs are comparable (same request text -> same work -> same load) for before/after memory
# comparisons. See README.md in this directory for usage and background.
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="https://timeseries.api.opendatahub.testingmachine.eu"
CONCURRENCY=4
ROUNDS=5
HEAVY_EVERY=1          # include requests-heavy.txt every Nth round; 0 disables it
ROUND_DELAY_SEC=0      # pause between rounds
WATCH_POD=""           # "namespace/pod-name" to poll `kubectl top` during the run
KUBE_CONTEXT="dev"
OUT_DIR=""
ENV_FILE="$HOME/.odh/test.env"
STRIP_V2=0              # 1 = strip a leading /v2 from each request path (see --no-v2)

usage() {
	cat <<EOF
Usage: $(basename "$0") [options]

  --base-url URL        Target ninja-api base URL (default: $BASE_URL)
  --concurrency N        Requests fired in parallel per batch (default: $CONCURRENCY)
  --rounds N              Number of rounds to run (default: $ROUNDS)
  --heavy-every N         Include requests-heavy.txt every Nth round, 0 = never (default: $HEAVY_EVERY)
  --round-delay SEC       Pause between rounds, in seconds (default: $ROUND_DELAY_SEC)
  --watch-pod NS/NAME     Poll 'kubectl top pod' for this pod every 5s for the run's duration
  --kube-context CTX      kubectl context to use with --watch-pod (default: $KUBE_CONTEXT)
  --env-file PATH         Credentials for the heavy (authenticated) set (default: $ENV_FILE)
  --out DIR               Output directory (default: results/<timestamp>/ under this script's dir)
  --no-v2                 Strip a leading /v2 from each request path - use when hitting a pod's
                           Service directly (e.g. via kubectl port-forward), which doesn't have
                           the /v2 prefix that the public hostname's front proxy adds
  -h, --help              Show this help
EOF
}

while [ $# -gt 0 ]; do
	case "$1" in
		--base-url) BASE_URL="$2"; shift 2 ;;
		--concurrency) CONCURRENCY="$2"; shift 2 ;;
		--rounds) ROUNDS="$2"; shift 2 ;;
		--heavy-every) HEAVY_EVERY="$2"; shift 2 ;;
		--round-delay) ROUND_DELAY_SEC="$2"; shift 2 ;;
		--watch-pod) WATCH_POD="$2"; shift 2 ;;
		--kube-context) KUBE_CONTEXT="$2"; shift 2 ;;
		--env-file) ENV_FILE="$2"; shift 2 ;;
		--out) OUT_DIR="$2"; shift 2 ;;
		--no-v2) STRIP_V2=1; shift 1 ;;
		-h|--help) usage; exit 0 ;;
		*) echo "Unknown option: $1" >&2; usage; exit 1 ;;
	esac
done

if [ -z "$OUT_DIR" ]; then
	OUT_DIR="$SCRIPT_DIR/results/$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$OUT_DIR"

REQUESTS_TSV="$OUT_DIR/requests.tsv"
MEMORY_TSV="$OUT_DIR/memory.tsv"
echo -e "round\tset\thttp_code\tbytes\ttime_total_s\turl" > "$REQUESTS_TSV"

echo "Output directory: $OUT_DIR"

# --- auth (only needed for the heavy set, which exceeds the anonymous rate limit) ---
TOKEN=""
if [ "$HEAVY_EVERY" != "0" ]; then
	if [ ! -f "$ENV_FILE" ]; then
		echo "WARNING: $ENV_FILE not found; heavy requests will run unauthenticated and likely hit the rate limit." >&2
	else
		# shellcheck disable=SC1090
		source "$ENV_FILE"
		TOKEN_JSON="$OUT_DIR/token.json"
		curl -s -X POST "$ODH_OAUTH_URI" \
			--data-urlencode "grant_type=client_credentials" \
			--data-urlencode "client_id=$ODH_OAUTH_CLIENT_ID" \
			--data-urlencode "client_secret=$ODH_OAUTH_CLIENT_SECRET" \
			-o "$TOKEN_JSON"
		TOKEN=$(python3 -c "import json; print(json.load(open('$TOKEN_JSON'))['access_token'])" 2>/dev/null)
		if [ -z "$TOKEN" ]; then
			echo "WARNING: failed to obtain a token from $ODH_OAUTH_URI; heavy requests will run unauthenticated." >&2
		fi
	fi
fi

# --- optional memory watch ---
WATCH_PID=""
if [ -n "$WATCH_POD" ]; then
	NS="${WATCH_POD%%/*}"
	POD="${WATCH_POD##*/}"
	echo -e "timestamp\tmemory" > "$MEMORY_TSV"
	(
		while true; do
			mem=$(kubectl --context "$KUBE_CONTEXT" top pod -n "$NS" "$POD" --no-headers 2>/dev/null | awk '{print $3}')
			[ -n "$mem" ] && echo -e "$(date +%H:%M:%S)\t$mem" >> "$MEMORY_TSV"
			sleep 5
		done
	) &
	WATCH_PID=$!
	echo "Watching memory for $WATCH_POD (context $KUBE_CONTEXT) -> $MEMORY_TSV (pid $WATCH_PID)"
fi

cleanup() {
	if [ -n "$WATCH_PID" ]; then
		kill "$WATCH_PID" 2>/dev/null
	fi
}
trap cleanup EXIT

fire_one() {
	local set_name="$1" round="$2" path="$3" auth_header="$4"
	if [ "$STRIP_V2" = "1" ]; then
		path="${path#/v2}"
	fi
	local url="$BASE_URL$path"
	local line
	if [ -n "$auth_header" ]; then
		line=$(curl -s -o /dev/null -w "%{http_code}\t%{size_download}\t%{time_total}" \
			-H "Authorization: Bearer $auth_header" --max-time 120 "$url")
	else
		line=$(curl -s -o /dev/null -w "%{http_code}\t%{size_download}\t%{time_total}" \
			--max-time 120 "$url")
	fi
	echo -e "$round\t$set_name\t$line\t$url" >> "$REQUESTS_TSV"
}

run_set() {
	# Tracks this batch's own PIDs and waits only on those - a bare `wait` would also block on
	# the (never-ending) memory-watch loop backgrounded in the main script, hanging forever.
	local set_name="$1" file="$2" round="$3" auth_header="$4"
	local batch_pids=()
	while IFS= read -r path; do
		[ -z "$path" ] && continue
		case "$path" in \#*) continue ;; esac
		fire_one "$set_name" "$round" "$path" "$auth_header" &
		batch_pids+=("$!")
		if [ "${#batch_pids[@]}" -ge "$CONCURRENCY" ]; then
			wait "${batch_pids[@]}"
			batch_pids=()
		fi
	done < "$file"
	if [ "${#batch_pids[@]}" -gt 0 ]; then
		wait "${batch_pids[@]}"
	fi
}

START_TS=$(date +%s)
for round in $(seq 1 "$ROUNDS"); do
	echo "=== round $round/$ROUNDS ==="
	run_set light "$SCRIPT_DIR/requests-light.txt" "$round" ""
	if [ "$HEAVY_EVERY" != "0" ] && [ $(( round % HEAVY_EVERY )) -eq 0 ]; then
		run_set heavy "$SCRIPT_DIR/requests-heavy.txt" "$round" "$TOKEN"
	fi
	if [ "$round" -lt "$ROUNDS" ] && [ "$ROUND_DELAY_SEC" != "0" ]; then
		sleep "$ROUND_DELAY_SEC"
	fi
done
END_TS=$(date +%s)

cleanup
trap - EXIT

echo ""
echo "=== summary ==="
python3 "$SCRIPT_DIR/summarize.py" "$REQUESTS_TSV" "$((END_TS - START_TS))"
echo "Full results: $REQUESTS_TSV"
if [ -n "$WATCH_POD" ]; then
	echo "Memory trace:  $MEMORY_TSV"
fi
exit 0
