# Load Tests

The `load-test` module now ships as a regular Java application that embeds Gatling. You can build a
distribution and drop it into any Java 21 container image; the app exposes the same simulations that
were previously launched through the Gradle Gatling plugin.

## Prerequisites
- Java 21 toolchain (Gradle will automatically provision one when running via `./gradlew`).
- A running instance of the `ledger` service that exposes the ingress endpoints.
- Enough funds in the configured asset account to cover the intended load duration.

## Available Simulations
- `MoveBetweenAccountsSimulation` — Initializes one asset account plus `N` liability accounts and
  continuously moves the configured transfer amount from the asset to a random liability account
  using the `Transfer.move` API.

Add additional simulations under `src/main/java/com/lekha/loadtest` and register them inside
`LoadTestApp`.

## Configuration
Simulations read their tuning parameters from JVM system properties (`-D...`) and environment
variables (system property takes precedence). Defaults remain the same as before.

| Setting | System Property | Environment Variable | Default | Notes |
| --- | --- | --- | --- | --- |
| Base URI of ledger ingress | `ledger.load.baseUri` | `LEDGER_LOAD_BASE_URI` | `http://localhost:8080` | Must point to the Restate HTTP endpoint that exposes `Account` and `Transfer`. |
| Liability accounts | `ledger.load.liabilityAccounts` | `LEDGER_LOAD_LIABILITY_ACCOUNTS` | `10` | Number of liability accounts initialized before the run; must be ≥ 1. |
| Transfer amount (minor units) | `ledger.load.transferMinorUnits` | `LEDGER_LOAD_TRANSFER_MINOR_UNITS` | `100` | Amount (in minor units) debited from the asset per move. |
| Concurrent virtual users | `ledger.load.concurrentUsers` | `LEDGER_LOAD_CONCURRENT_USERS` | `10` | Drives Gatling’s `constantConcurrentUsers`. |
| Duration (seconds) | `ledger.load.durationSeconds` | `LEDGER_LOAD_DURATION_SECONDS` | `60` | Total wall-clock duration for the run (`maxDuration`). |
| Simulation selector | — | `LEDGER_LOAD_SIMULATION` | `MoveBetweenAccountsSimulation` | Also configurable via the first CLI argument. |
| Custom results directory | — | `LEDGER_LOAD_RESULTS_DIR` | Gatling default | Useful when persisting reports outside the container. |

## Running Locally
Launch a simulation directly through Gradle:

```bash
LEDGER_LOAD_BASE_URI=http://localhost:8080 \
LEDGER_LOAD_LIABILITY_ACCOUNTS=25 \
./gradlew :load-test:run --args=MoveBetweenAccountsSimulation
```

Omit `--args` to fall back to the default simulation or pass a fully-qualified class name for
custom scenarios on the classpath.

## Packaging for Containers

Create an installable distribution that bundles all runtime dependencies:

```bash
./gradlew :load-test:installDist
```

Copy `load-test/build/install/load-test` into your image (or use `distTar`/`distZip`) and invoke the
provided launcher script:

```bash
LEDGER_LOAD_BASE_URI=https://ledger.example.com \
LEDGER_LOAD_SIMULATION=MoveBetweenAccountsSimulation \
/opt/load-test/bin/load-test
```

## Reports
Gatling HTML reports land under `load-test/build/reports/gatling/<simulation-name>/`. When running
inside a container, set `LEDGER_LOAD_RESULTS_DIR` to an attached volume so you can collect the
reports post-run. Each virtual user’s last error remains available as the `lastError` session
attribute.

## Tips
- Tail the ledger service logs alongside the load test output to quickly spot initialization or API
  errors.
- Increase `LEDGER_LOAD_TRANSFER_MINOR_UNITS` and the number of liability accounts together to avoid
  draining the asset account too early during long scenarios.
- Document any new simulations in this file and register them in `LoadTestApp` so operators can
  discover them easily.

## Docker Compose Stack
The repository root now includes a `docker-compose.yml` that wires Jaeger, Restate runtime, the
ledger service, and the load-test app. Build and launch everything with:

```bash
docker compose up --build
```

Compose waits for the dependencies first (`jaeger` → `runtime` → `ledger`) before starting the load
test container, which targets the runtime at `http://runtime:8080`. Gatling reports from the load
test are written to `load-test/reports` on the host through a volume mount, so they remain available
after the container exits. Tweak environment variables in `docker-compose.yml` to change load test
settings or expose different ports.
