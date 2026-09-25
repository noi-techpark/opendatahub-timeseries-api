#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# psql / pg_dump against the database of JDBC_URL, DB_USERNAME and DB_PASSWORD (from ./.env or the
# environment), without the credentials showing up in commands or output.
# usage: tools/realdata/psql.sh [psql|pg_dump] <arguments...>
set -eu
cd "$(dirname "$0")/../.."
# read only the three variables, .env is not necessarily valid shell
for v in JDBC_URL DB_USERNAME DB_PASSWORD; do
  if [ -z "${!v:-}" ] && [ -f .env ]; then export "$v=$(grep -E "^$v=" .env | head -1 | cut -d= -f2-)"; fi
done
tool=$1; shift
url=${JDBC_URL#jdbc:postgresql://}; hostport=${url%%/*}; rest=${url#*/}; db=${rest%%\?*}
export PGPASSWORD="$DB_PASSWORD"   # handed to the container by name, so it is not part of any command line
exec docker run --rm -i --network host \
  -e PGPASSWORD -e PGOPTIONS="-c default_transaction_read_only=on -c search_path=intimev2,public -c statement_timeout=${STATEMENT_TIMEOUT_MS:-180000}" \
  -e PGCONNECT_TIMEOUT=20 postgres:17 "$tool" -h "${hostport%%:*}" -p "${hostport##*:}" -U "$DB_USERNAME" -d "$db" "$@"
