#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Re-records the expected responses of the contract tests from the reference implementation (default: main)
# and puts them into this working tree:
#   src/test/resources/contract/golden            (invented data: routing, errors, roles, quotas, ...)
#   src/test/resources/contract/golden-snapshot   (the real, public data snapshot)
# Review the git diff afterwards: a change means the behavior of the API differs from the reference.
#   REF=main tools/realdata/record-goldens.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$PWD
REFDIR=$(tools/realdata/reference-build.sh | tail -1)
rm -rf "$REFDIR/src/test/resources/contract/golden" "$REFDIR/src/test/resources/contract/golden-snapshot"
(cd "$REFDIR" && mvn -q -B test -Dcontract.record=true \
   -Dtest='ApiContractTest,LimitedApiContractTest,SnapshotContractTest,FuzzContractTest' -DfailIfNoTests=false 2>&1 |
   grep -E "Tests run:|ERROR\]" | head -10) || true
for d in golden golden-snapshot; do
  rm -rf "$ROOT/src/test/resources/contract/$d"
  cp -r "$REFDIR/src/test/resources/contract/$d" "$ROOT/src/test/resources/contract/$d"
  echo "$d: $(ls "$ROOT/src/test/resources/contract/$d" | wc -l) responses"
done
