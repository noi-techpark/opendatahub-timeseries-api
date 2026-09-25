#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Benchmark of this branch against the reference (default: main) on the local copy of the real data.
# The heavy requests of requests-real.txt run against both builds with the same heap and are compared.
#   tools/bench/run.sh [heap]          (default 512m)
#   REF=<git ref> tools/bench/run.sh 1g
set -euo pipefail
cd "$(dirname "$0")/../.."
HEAP=${1:-512m}
PORT_DB=${LOCAL_PORT:-55433}
export JDBC_URL="jdbc:postgresql://localhost:$PORT_DB/bdp?currentSchema=intimev2,public" DB_USERNAME=postgres DB_PASSWORD=pw
REFDIR=$(tools/realdata/reference-build.sh | tail -1)
mvn -q -B -DskipTests package
mkdir -p target/bench
cp "$REFDIR/target/v2.jar" target/bench/reference.jar
cp target/v2.jar target/bench/branch.jar

# the most recent 3 days found in the data (ignoring bogus timestamps in the future)
read -r FROM TO < <(docker exec ninja-realdata psql -U postgres -d bdp -Atc "select to_char(m - interval '3 days', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') || ' ' || to_char(m + interval '1 minute', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') from (select max(\"timestamp\") m from intimev2.measurementhistory where \"timestamp\" <= now()) x")
sed "s/{FROM}/$FROM/g; s/{TO}/$TO/g" tools/bench/requests-real.txt > target/bench/requests.txt

echo "heap $HEAP, data from $FROM to $TO"
tools/bench/compare.sh target/bench/requests.txt "$HEAP" "reference=target/bench/reference.jar" "branch=target/bench/branch.jar" | tee target/bench/result.tsv
echo
echo "raw results: target/bench/result.tsv"
