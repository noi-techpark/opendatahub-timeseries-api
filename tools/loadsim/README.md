# loadsim

Replays a fixed set of real request patterns against ninja-api for repeatable memory/load testing.
Requests are pinned (no `now()`-relative timestamps), so the same run does the same work every
time — useful for before/after comparisons.

## Run

```sh
# quick smoke run
./run.sh --rounds 1 --concurrency 2

# before/after comparison, watching pod memory throughout
./run.sh --rounds 10 --concurrency 6 --heavy-every 2 \
    --watch-pod core/ninja-api-xxxxxxxxxx-xxxxx --kube-context dev
```

Results: `results/<timestamp>/requests.tsv` (per-request status/bytes/time) and `memory.tsv`
(`kubectl top pod` samples every 5s, if `--watch-pod` given). Summary prints at the end, or
regenerate with `python3 summarize.py results/<timestamp>/requests.tsv`.

## Options

```
--base-url URL        target (default: timeseries.api.opendatahub.testingmachine.eu)
--concurrency N        parallel requests per batch (default: 4)
--rounds N              number of rounds (default: 5)
--heavy-every N         include requests-heavy.txt every Nth round, 0 = never (default: 1)
--round-delay SEC       pause between rounds (default: 0)
--watch-pod NS/NAME     poll `kubectl top pod` every 5s for the run's duration
--kube-context CTX      kubectl context for --watch-pod (default: dev)
--env-file PATH         credentials for the heavy/authenticated set (default: ~/.odh/test.env)
--out DIR               output dir (default: results/<timestamp>/)
```

`requests-light.txt` = small unauthenticated requests. `requests-heavy.txt` = large-response
requests, needs `--env-file` (client_credentials, exceeds anonymous rate limit otherwise).

No confirmation prompt and no built-in memory abort — don't point `--base-url` at prod without
thinking about it, and Ctrl-C if `memory.tsv`/your dashboard looks wrong.
