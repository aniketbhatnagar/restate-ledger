# Load Tests

The `load-test` module hosts Gatling simulations that exercise the Restate-based ledger under
realistic, high-concurrency workloads. The simulations call the same ingress handlers exposed by the
`ledger` module, so you can reuse them to benchmark local and remote deployments.


## Prerequisites
- Java 21 toolchain (Gradle will download the exact version if you use `./gradlew`).
- The ledger service from this repo running and reachable at `LEDGER_LOAD_BASE_URI`
  (defaults to `http://localhost:8080`).
- Sufficient funds in the configured asset account for your test duration, or the ability to accept
  the automatic account initialization performed by the simulation.


## Available Simulations
- `MoveBetweenAccountsSimulation` &mdash; Initializes one asset account plus `N` liability accounts,
  then continuously moves the configured transfer amount from the asset to a random liability
  account using the `Transfer.move` API. The number of concurrent virtual users and the total test
  duration are configurable (see below).

More simulations may be added later; list them here as they are introduced.


## Configuration
Simulations derive their settings from either JVM system properties (`-D...`) or environment
variables. System properties take precedence, then environment variables, and finally the default
value in code.

| Setting | System Property | Environment Variable | Default | Notes |
| --- | --- | --- | --- | --- |
| Base URI of ledger ingress | `ledger.load.baseUri` | `LEDGER_LOAD_BASE_URI` | `http://localhost:8080` | Must point to the Restate HTTP endpoint that exposes `Account` and `Transfer` handlers. |
| Liability accounts | `ledger.load.liabilityAccounts` | `LEDGER_LOAD_LIABILITY_ACCOUNTS` | `10` | Number of liability accounts initialized before the run. Must be ≥ 1. |
| Transfer amount (minor units) | `ledger.load.transferMinorUnits` | `LEDGER_LOAD_TRANSFER_MINOR_UNITS` | `100` | Amount (in the smallest currency unit) debited from the asset per move. Currency is fixed to USD today. |
| Concurrent virtual users | `ledger.load.concurrentUsers` | `LEDGER_LOAD_CONCURRENT_USERS` | `10` | Determines Gatling’s `constantConcurrentUsers`. |
| Duration (seconds) | `ledger.load.durationSeconds` | `LEDGER_LOAD_DURATION_SECONDS` | `60` | Total wall-clock time the scenario runs. Gatling also uses it as `maxDuration`. |


## Running a Simulation
From the repo root, run one of the following Gradle tasks (the simulation name is appended to
`gatlingRun-`):

```bash
./gradlew :load-test:gatlingRun-MoveBetweenAccountsSimulation
```

Override configuration inline as needed; for example, to hit a remote deployment with more load:

```bash
LEDGER_LOAD_BASE_URI=https://ledger.example.com \
LEDGER_LOAD_LIABILITY_ACCOUNTS=50 \
LEDGER_LOAD_TRANSFER_MINOR_UNITS=5000 \
LEDGER_LOAD_CONCURRENT_USERS=75 \
LEDGER_LOAD_DURATION_SECONDS=300 \
./gradlew :load-test:gatlingRun-MoveBetweenAccountsSimulation
```

You can use JVM system properties instead when invoking Gradle:

```bash
./gradlew :load-test:gatlingRun-MoveBetweenAccountsSimulation \
  -Dledger.load.concurrentUsers=25 \
  -Dledger.load.durationSeconds=180
```


## Reports
Gatling HTML reports are generated under `load-test/build/reports/gatling/<simulation-name>/`. Open
`index.html` from the relevant directory to inspect response times, percentiles, and failures. The
last error seen by a virtual user is also stored in the Gatling session under the `lastError`
attribute for easier debugging.


## Tips
- Keep an eye on the ledger service logs while the simulation runs; initialization errors are
  surfaced there as well as in the Gatling console output.
- Scale `LEDGER_LOAD_TRANSFER_MINOR_UNITS` along with the number of liability accounts to avoid
  draining the asset account too quickly during long runs.
- Commit any new simulations alongside updated documentation in this file so that future operators
  know how to exercise them.
