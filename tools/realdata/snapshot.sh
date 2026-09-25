#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Builds the small, committed test data set from the local copy of the real data (tools/realdata/extract.sh):
# only what an anonymous caller may see (the guest ACL rules of the API itself decide), a few stations per
# station type, the latest values and a few hours of history.
#   tools/realdata/snapshot.sh          -> src/test/resources/contract/snapshot/{schema.sql,data.sql.gz}
#
# Tunables: STATIONS_PER_TYPE (default 8), HISTORY_HOURS (default 6), METADATA_HISTORY (default 3), EVENTS (default 60)
set -euo pipefail
cd "$(dirname "$0")/../.."
N=${STATIONS_PER_TYPE:-8}; HOURS=${HISTORY_HOURS:-6}; MDH=${METADATA_HISTORY:-3}; EVENTS=${EVENTS:-60}
OUT=src/test/resources/contract/snapshot
C=ninja-realdata
psqlc() { docker exec -i $C psql -q -v ON_ERROR_STOP=1 -U postgres "$@"; }
strip() { grep -v '^\s*--' "$1" | tr '\n' ' '; }
GUEST_STATION=$(strip src/main/resources/acl-rules/stations/GUEST.sql)
GUEST_EVENT=$(strip src/main/resources/acl-rules/events/GUEST.sql)

echo "== working copy"
psqlc -d postgres -c "drop database if exists snapshot" -c "create database snapshot template bdp" 
psqlc -d snapshot -c "alter database snapshot set search_path = intimev2, public"
S="psqlc -d snapshot"

echo "== keep only what is public"
$S <<SQL
create temp table vis as
  select ts.id as ts_id, s.id as station_id, s.stationtype
  from timeseries ts join station s on s.id = ts.station_id join type t on t.id = ts.type_id
  where s.available and ($GUEST_STATION);
create temp table picked as
  select station_id from (select station_id, stationtype, dense_rank() over (partition by stationtype order by station_id) r from vis) x where r <= $N;
create temp table keep_ts as select ts_id as id from vis where station_id in (select station_id from picked);
create temp table keep_station as
  select station_id as id from picked
  union select parent_id from station where id in (select station_id from picked) and parent_id is not null
  union select edge_data_id from edge where edge_data_id is not null union select origin_id from edge where origin_id is not null
  union select destination_id from edge where destination_id is not null;

delete from measurement where timeseries_id not in (select id from keep_ts);
delete from measurementstring where timeseries_id not in (select id from keep_ts);
delete from measurementjson where timeseries_id not in (select id from keep_ts);
create temp table cutoff as select (select max("timestamp") from measurementhistory where "timestamp" <= now()) - interval '$HOURS hours' as t;
delete from measurementhistory where timeseries_id not in (select id from keep_ts) or "timestamp" < (select t from cutoff) or "timestamp" > now();
delete from measurementstringhistory where timeseries_id not in (select id from keep_ts) or "timestamp" < (select t from cutoff) or "timestamp" > now();
delete from measurementjsonhistory where timeseries_id not in (select id from keep_ts) or "timestamp" < (select t from cutoff) or "timestamp" > now();
delete from timeseries where id not in (select id from keep_ts);
delete from station where id not in (select id from keep_station);
-- the newest metadata versions per station plus the one the station points to
delete from metadata where id in (
  select id from (select m.id, row_number() over (partition by m.station_id order by m.created_on desc) r, m.station_id from metadata m) x
  where x.station_id is not null and x.r > $MDH
  and id not in (select meta_data_id from station where meta_data_id is not null));
delete from metadata where station_id is not null and station_id not in (select id from station);
delete from metadata where station_id is null and id not in (select meta_data_id from station where meta_data_id is not null)
  and id not in (select meta_data_id from event where meta_data_id is not null);
delete from event ev where not ($GUEST_EVENT);
delete from event where id not in (select id from (select id from event order by created_on desc limit $EVENTS) x);
delete from location where id not in (select location_id from event where location_id is not null);
-- internal configuration of the database, not served by the API
delete from partition;
delete from partition_def;
delete from type where id not in (select type_id from timeseries);
delete from type_metadata where id not in (select meta_data_id from type where meta_data_id is not null);
delete from provenance where id not in (
  select provenance_id from measurement where provenance_id is not null union select provenance_id from measurementstring where provenance_id is not null
  union select provenance_id from measurementjson where provenance_id is not null union select provenance_id from measurementhistory where provenance_id is not null
  union select provenance_id from measurementstringhistory where provenance_id is not null union select provenance_id from measurementjsonhistory where provenance_id is not null
  union select provenance_id from event);
SQL

echo "== dump"
mkdir -p $OUT
# the schema of the local copy: the real tables, without foreign keys, defaults and partitioning
{ echo "create extension if not exists postgis;"
  docker exec $C pg_dump -U postgres -d snapshot --schema-only --no-owner --no-privileges --no-comments -n intimev2 |
    grep -vE '^(SET |SELECT pg_catalog\.set_config|\\(un)?restrict)' |
    sed 's/^CREATE SCHEMA intimev2;/CREATE SCHEMA IF NOT EXISTS intimev2;/'; } > $OUT/schema.sql
docker exec $C pg_dump -U postgres -d snapshot --data-only --inserts --rows-per-insert=100 --no-owner --no-privileges -n intimev2 \
  -T 'intimev2.flyway*' --column-inserts 2>/dev/null |
  grep -vE '^(SET|SELECT pg_catalog|\\)|^--' | gzip -9 -n > $OUT/data.sql.gz
psqlc -d postgres -c "drop database snapshot"
ls -la $OUT
