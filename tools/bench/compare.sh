#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Runs the same requests against one or more application jars and reports status, size, time and
# the peak memory (RSS) of the process, so two builds can be compared.
#
# usage: tools/bench/compare.sh <requests-file> <heap> <label=jar> [<label=jar> ...]
#   requests-file: one "name path" per line (path with query string), '#' for comments
#   heap:          -Xmx for every run, e.g. 512m
# The database is taken from the environment (JDBC_URL, DB_USERNAME, DB_PASSWORD), for example
# from a local copy of the data:  set -a; . ./.env; set +a
set -u
REQ=$1; HEAP=$2; shift 2
PORT=${BENCH_PORT:-18090}
export SERVER_PORT=$PORT NINJA_BASE_URL=http://localhost:$PORT NINJA_HOST_URL=http://localhost:$PORT
export NINJA_QUOTA_GUEST=100000 NINJA_QUOTA_URL=x NINJA_QUERY_TIMEOUT_SEC=${NINJA_QUERY_TIMEOUT_SEC:-600}
export NINJA_RESPONSE_MAX_SIZE_MB=${NINJA_RESPONSE_MAX_SIZE_MB:-0} KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET:-x}
for spec in "$@"; do
  label=${spec%%=*}; jar=${spec#*=}
  java -Xmx$HEAP -Duser.timezone=UTC -jar "$jar" --logging.level.root=WARN >"/tmp/bench-$label.log" 2>&1 &
  pid=$!
  for _ in $(seq 90); do curl -s -o /dev/null "http://localhost:$PORT/" && break; sleep 1; done
  while read -r name path; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    out=$(curl -s -o /dev/null --max-time "${BENCH_TIMEOUT:-600}" -w '%{http_code} %{size_download} %{time_total}' "http://localhost:$PORT$path")
    alive=$(kill -0 $pid 2>/dev/null && echo y || echo n)
    printf '%s\t%s\t%s\tstatus=%s bytes=%s seconds=%s peak_rss_mb=%s alive=%s\n' "$label" "$name" "$path" \
      $(echo $out | cut -d' ' -f1) $(echo $out | cut -d' ' -f2) $(echo $out | cut -d' ' -f3) \
      "$(( $(grep VmHWM /proc/$pid/status | awk '{print $2}') / 1024 ))" "$alive"
  done <"$REQ"
  kill $pid 2>/dev/null; wait $pid 2>/dev/null
done
