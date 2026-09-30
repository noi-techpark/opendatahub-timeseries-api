#!/usr/bin/env python3
# SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Summarizes a requests.tsv produced by run.sh: totals, per-set breakdown, and any non-2xx."""
import csv
import sys
from collections import defaultdict


def main():
    path = sys.argv[1]
    elapsed_s = int(sys.argv[2]) if len(sys.argv) > 2 else None

    with open(path, newline="") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))

    if not rows:
        print("no requests recorded")
        return

    by_set = defaultdict(lambda: {"n": 0, "bytes": 0, "time": 0.0, "errors": 0})
    total_bytes = 0
    total_time = 0.0
    errors = []

    for r in rows:
        s = by_set[r["set"]]
        s["n"] += 1
        try:
            b = int(r["bytes"])
        except ValueError:
            b = 0
        try:
            t = float(r["time_total_s"])
        except ValueError:
            t = 0.0
        s["bytes"] += b
        s["time"] += t
        total_bytes += b
        total_time += t
        code = r["http_code"]
        if not code.startswith("2"):
            s["errors"] += 1
            errors.append((r["round"], r["set"], code, r["url"]))

    print(f"requests: {len(rows)}")
    print(f"total response bytes: {total_bytes:,} ({total_bytes / (1024*1024):.1f} MiB)")
    print(f"total cumulative request time: {total_time:.1f}s")
    if elapsed_s is not None:
        print(f"wall-clock duration: {elapsed_s}s")
    print()
    print(f"{'set':<8}{'requests':>10}{'bytes':>16}{'avg ms':>10}{'errors':>8}")
    for name, s in sorted(by_set.items()):
        avg_ms = (s["time"] / s["n"] * 1000) if s["n"] else 0
        print(f"{name:<8}{s['n']:>10}{s['bytes']:>16,}{avg_ms:>10.0f}{s['errors']:>8}")

    if errors:
        print(f"\n{len(errors)} non-2xx response(s):")
        for round_, set_, code, url in errors[:20]:
            print(f"  round {round_} [{set_}] HTTP {code}  {url}")
        if len(errors) > 20:
            print(f"  ... and {len(errors) - 20} more")


if __name__ == "__main__":
    main()
