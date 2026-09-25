#!/bin/bash
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: CC0-1.0
#
# Checks out the reference implementation (default: main) into a separate worktree, gives it the contract
# test harness of this branch and builds its jar. Prints the directory on the last line.
#   REF=main REFDIR=/tmp/ninja-reference tools/realdata/reference-build.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$PWD
REF=${REF:-main}
REFDIR=${REFDIR:-/tmp/ninja-reference}
if [ -d "$REFDIR" ]; then git worktree remove --force "$REFDIR"; fi
git worktree add -q --detach "$REFDIR" "$REF"

# the harness, adapted to the older test libraries of the reference (Testcontainers 1.x)
T=src/test/java/com/opendatahub/api/timeseries/ninja/contract
mkdir -p "$REFDIR/$T" "$REFDIR/src/test/resources/contract"
cp $T/*.java "$REFDIR/$T/"
cp src/test/resources/contract/*.sql "$REFDIR/src/test/resources/contract/"
cp -r src/test/resources/contract/snapshot "$REFDIR/src/test/resources/contract/"
sed -i 's/org.testcontainers.postgresql.PostgreSQLContainer/org.testcontainers.containers.PostgreSQLContainer/; s/final PostgreSQLContainer db;/final PostgreSQLContainer<?> db;/; s/new PostgreSQLContainer(/new PostgreSQLContainer<>(/' "$REFDIR/$T/ContractEnv.java"
python3 - "$REFDIR/pom.xml" <<'PY'
import sys
p=sys.argv[1]; s=open(p).read()
if 'testcontainers' not in s:
    s=s.replace("		<!-- Logging -->","		<dependency>\n			<groupId>org.testcontainers</groupId>\n			<artifactId>postgresql</artifactId>\n			<version>1.21.4</version>\n			<scope>test</scope>\n		</dependency>\n\n		<!-- Logging -->")
    s=s.replace("""				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>""","""				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-surefire-plugin</artifactId>
				<configuration>
					<reuseForks>false</reuseForks>
					<systemPropertyVariables><contract.record>${contract.record}</contract.record></systemPropertyVariables>
				</configuration>
			</plugin>""")
    s=s.replace("<finalName>v2</finalName>","<finalName>v2</finalName>\n		<contract.record>false</contract.record>")
open(p,'w').write(s)
PY
(cd "$REFDIR" && mvn -q -B -DskipTests package >&2)
echo "$REFDIR"
