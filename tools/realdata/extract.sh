#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Copies a limited, recent slice of the real database (JDBC_URL, DB_USERNAME, DB_PASSWORD from ./.env or
# the environment; only ever read) into a local PostGIS container, for repeatable tests and benchmarks.
#
#   tools/realdata/extract.sh            (re)creates the container "ninja-realdata" on port 55433
#
# Tunables (environment):
#   HISTORY_DAYS   days of measurement history to copy                 (default 3)
#   LIMITS         station type:max stations, comma separated          (see below)
#   LOCAL_PORT     port of the local database                          (default 55433)
# The result is deterministic apart from the moving window of recent data.
set -euo pipefail
cd "$(dirname "$0")/../.."
HISTORY_DAYS=${HISTORY_DAYS:-3}
LOCAL_PORT=${LOCAL_PORT:-55433}
LIMITS=${LIMITS:-ParkingStation:300,MeteoStation:200,EChargingStation:300,TrafficSensor:100,BluetoothStation:100,BikesharingStation:200,EnvironmentStation:200,LinkStation:400,RWISstation:50}
NAME=ninja-realdata
REMOTE="tools/realdata/psql.sh psql -q"
LOCAL="docker exec -i $NAME psql -q -v ON_ERROR_STOP=1 -U postgres -d bdp"

if [ "${ONLY_HISTORY:-0}" != 1 ]; then
echo "== local database"
docker rm -f $NAME >/dev/null 2>&1 || true
docker run -d --name $NAME -e POSTGRES_PASSWORD=pw -e POSTGRES_DB=bdp -p $LOCAL_PORT:5432 postgis/postgis:16-3.5-alpine \
  -c fsync=off -c synchronous_commit=off -c full_page_writes=off >/dev/null
until docker exec $NAME pg_isready -U postgres -d bdp >/dev/null 2>&1; do sleep 1; done
sleep 3

echo "== schema"
tools/realdata/psql.sh pg_dump --schema-only --no-owner --no-privileges --no-comments -n intimev2 \
  $(for t in station metadata type type_metadata provenance timeseries measurement measurementstring measurementjson \
             measurementhistory measurementstringhistory measurementjsonhistory edge location event partition partition_def; do echo -t intimev2.$t; done) |
  python3 -c '
import re,sys
s=sys.stdin.read()
s=re.sub(r"^\\(un)?restrict .*$","",s,flags=re.M)
s=s.replace("SET transaction_timeout = 0;","")
s=re.sub(r"\nPARTITION BY LIST \(partition_id\);",";",s)                       # history tables as plain tables
s=re.sub(r"DEFAULT nextval\([^)]*\)( NOT NULL)?", r"\1", s)                    # no sequences needed
s=re.sub(r"ALTER TABLE (ONLY )?[^;]*?\n\s+ADD CONSTRAINT [^;]*FOREIGN KEY[^;]*;","",s,flags=re.S)
s=re.sub(r"CREATE (UNIQUE )?INDEX (\S+) ON ONLY ",r"CREATE \1INDEX \2 ON ",s)
sys.stdout.write("CREATE EXTENSION IF NOT EXISTS postgis;\nCREATE SCHEMA IF NOT EXISTS intimev2;\n"+s)
' | $LOCAL >/dev/null

$LOCAL -c "alter database bdp set search_path = intimev2, public"
fi

# ---- what to copy -----------------------------------------------------------------------------
PICK="select id from (select id, stationtype, row_number() over (partition by stationtype order by id) rn from station where available) p where $(echo "$LIMITS" | tr ',' '\n' | awk -F: '{printf "%s(stationtype = %c%s%c and rn <= %s)", (NR==1?"":" or "),39,$1,39,$2}')"
# plugs of the picked charging stations, parents, and the stations of all edges
BASE="select id from ($PICK) a
      union select id from station where available and parent_id in ($PICK)"
SEL="select id from station where available and (id in ($BASE)
      or id in (select parent_id from station where id in ($BASE) and parent_id is not null)
      or id in (select edge_data_id from edge union select origin_id from edge union select destination_id from edge))"
TS="select id from timeseries where station_id in ($SEL)"
SINCE="(now() at time zone 'utc') - interval '$HISTORY_DAYS days'"

copy() { # table query
  echo "  $1"
  $REMOTE -c "\\copy ($2) to stdout" | $LOCAL -c "\\copy $1 from stdin"
}

echo "== data ($HISTORY_DAYS days of history, limits: $LIMITS)"
if [ "${ONLY_HISTORY:-0}" != 1 ]; then
  copy provenance        "select * from provenance"
  copy partition         "select * from partition"
  copy partition_def     "select * from partition_def"
  copy station           "select * from station where id in ($SEL)"
  copy metadata          "select * from metadata where station_id in ($SEL) or id in (select meta_data_id from station where id in ($SEL))"
  copy type              "select * from type"
  copy type_metadata     "select * from type_metadata where id in (select meta_data_id from type where meta_data_id is not null)"
  copy timeseries        "select * from timeseries where station_id in ($SEL)"
  copy edge              "select * from edge"
  copy event             "select * from event"
  copy location          "select * from location where id in (select location_id from event where location_id is not null)"
fi
for t in measurement measurementstring measurementjson; do
  $LOCAL -c "truncate $t"
  copy $t              "select * from $t where timeseries_id in ($TS)"
done

# The history tables have billions of rows: ask for explicit series (index on timeseries_id, timestamp) in
# small chunks, never with a subquery, which can make the planner scan the partitions.
echo "  history ($HISTORY_DAYS days)"
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
$REMOTE -Atc "select id from ($TS) x order by id" > "$WORK/ids"
split -l ${CHUNK:-100} "$WORK/ids" "$WORK/chunk_"
HIST_TABLES=${HIST_TABLES:-measurement measurementstring measurementjson}
for t in $HIST_TABLES; do $LOCAL -c "truncate ${t}history"; done
for t in $HIST_TABLES; do
  cols="*"; into="${t}history"
  # the real json history has a generated column (md5 of the value) that the copy does not carry
  if [ "$t" = measurementjson ]; then cols='created_on, "timestamp", json_value, provenance_id, timeseries_id, partition_id'; into="${t}history ($cols)"; fi
  for c in "$WORK"/chunk_*; do
    ids=$(paste -sd, "$c")
    $REMOTE -c "\\copy (select $cols from ${t}history where timeseries_id = any('{$ids}'::int[]) and \"timestamp\" >= $SINCE) to stdout" |
      $LOCAL -c "\\copy $into from stdin"
  done
  echo "    ${t}history: $($LOCAL -Atc "select count(*) from ${t}history")"
done

echo "== analyze"
$LOCAL -c "analyze"
$LOCAL -Atc "select 'station', count(*) from intimev2.station union all select 'timeseries', count(*) from intimev2.timeseries union all select 'measurementhistory', count(*) from intimev2.measurementhistory union all select 'metadata', count(*) from intimev2.metadata union all select 'size', pg_database_size('bdp') / 1048576"
echo "local database: jdbc:postgresql://localhost:$LOCAL_PORT/bdp?currentSchema=intimev2,public (postgres/pw)"
