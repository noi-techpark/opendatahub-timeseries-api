#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Before/after check on the local copy of the real data (tools/realdata/extract.sh):
#   1. records the responses of the reference implementation (default: main) for requests derived from the data
#   2. runs the same requests against this branch and compares
# usage: tools/realdata/compare.sh            (REF=<git ref> to compare with something else than main)
set -euo pipefail
cd "$(dirname "$0")/../.."
URL=${CONTRACT_DB_URL:-jdbc:postgresql://localhost:${LOCAL_PORT:-55433}/bdp}
GOLDEN=${GOLDEN:-$PWD/target/golden-real}
REFDIR=$(tools/realdata/reference-build.sh | tail -1)
rm -rf "$GOLDEN"; mkdir -p "$GOLDEN"
ARGS="-B -Dtest=SnapshotContractTest -DfailIfNoTests=false -Dcontract.db.url=$URL -Dcontract.golden=$GOLDEN"
echo "== recording ${REF:-main} from the reference implementation"
(cd "$REFDIR" && mvn -q $ARGS test -Dcontract.record=true 2>&1 | grep -E "Tests run:|ERROR\]" | head -5) || true
echo "recorded: $(ls "$GOLDEN" | wc -l) responses"
echo "== verifying this branch"
mvn -B $ARGS test 2>&1 | grep -E "Tests run:|contract mismatch|BUILD" | cut -c1-220
